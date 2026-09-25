package com.brainserve.clientonboarding.assets.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import com.brainserve.clientonboarding.assets.application.AssetStorage.SignedUrl;
import com.brainserve.clientonboarding.common.error.DomainException;

@Testcontainers(disabledWithoutDocker=true)
class S3AssetStorageTest {
    @Container static final GenericContainer<?> S3=new GenericContainer<>(DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z"))
            .withEnv("MINIO_ROOT_USER","test-storage-user").withEnv("MINIO_ROOT_PASSWORD","test-storage-password")
            .withCommand("server","/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000)).withStartupTimeout(Duration.ofMinutes(2));
    static String endpoint;static S3Client admin;static S3AssetStorage storage;
    @BeforeAll static void provision(){
        endpoint="http://"+S3.getHost()+":"+S3.getMappedPort(9000);
        admin=S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint)).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-storage-user","test-storage-password")))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        admin.createBucket(b->b.bucket("asset-test"));admin.putBucketVersioning(b->b.bucket("asset-test").versioningConfiguration(v->v.status(BucketVersioningStatus.ENABLED)));
        var p=new AssetProperties();p.setEnabled(true);p.setEndpoint(endpoint);p.setPublicEndpoint(endpoint);p.setBucket("asset-test");p.setAccessKey("test-storage-user");p.setSecretKey("test-storage-password");storage=new S3AssetStorage(p);
    }
    @AfterAll static void close(){if(storage!=null)storage.close();if(admin!=null)admin.close();}
    @Test void signedUploadsBindMetadataAndPinnedDownloadsSurviveReusedUploadUrl()throws Exception {
        String key=UUID.randomUUID()+"/"+UUID.randomUUID();byte[] original="original file".getBytes(StandardCharsets.UTF_8);
        var signed=storage.upload(key,"text/plain",original.length,"a".repeat(64));
        assertThat(put(signed,original,null).statusCode()).isEqualTo(200);
        var first=storage.inspect(key,null);assertThat(first.versionId()).isNotBlank();assertThat(first.size()).isEqualTo(original.length);
        assertThat(put(signed,"changed bytes".getBytes(StandardCharsets.UTF_8),null).statusCode()).isEqualTo(412);
        // Even a privileged storage-side overwrite cannot change the file selected by its immutable version ID.
        admin.putObject(b->b.bucket("asset-test").key(key),software.amazon.awssdk.core.sync.RequestBody.fromString("changed bytes"));
        assertThat(storage.inspect(key,null).versionId()).isNotEqualTo(first.versionId());
        assertThat(put(signed,original,"application/pdf").statusCode()).isEqualTo(403);
        assertThat(put(signed,"short".getBytes(StandardCharsets.UTF_8),null).statusCode()).isEqualTo(403);
        var download=storage.download(key,first.versionId(),"brand.txt");
        var http=HttpClient.newHttpClient();var fetched=http.send(HttpRequest.newBuilder(URI.create(download.url())).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(fetched.statusCode()).isEqualTo(200);assertThat(fetched.body()).isEqualTo("original file");
        assertThat(fetched.headers().firstValue("content-disposition")).hasValue("attachment; filename=\"brand.txt\"");
        assertThat(fetched.headers().firstValue("content-type")).hasValue("application/octet-stream");
        assertThat(http.send(HttpRequest.newBuilder(URI.create(endpoint+"/asset-test/"+key)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
    }
    @Test void unversionedBucketsAndDisabledStorageFailClosed(){
        admin.createBucket(b->b.bucket("unversioned-test"));
        var p=new AssetProperties();p.setEnabled(true);p.setEndpoint(endpoint);p.setPublicEndpoint(endpoint);p.setBucket("unversioned-test");p.setAccessKey("test-storage-user");p.setSecretKey("test-storage-password");
        var unversioned=new S3AssetStorage(p);
        try{assertThatThrownBy(()->unversioned.upload("file","text/plain",1,"a".repeat(64))).isInstanceOf(DomainException.class);}finally{unversioned.close();}
        var disabled=new S3AssetStorage(new AssetProperties());
        assertThatThrownBy(()->disabled.download("file","version","file.txt")).isInstanceOf(DomainException.class);
    }
    private HttpResponse<String> put(SignedUrl signed,byte[] body,String mime)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(signed.url())).timeout(Duration.ofSeconds(20));
        signed.headers().forEach((key,value)->request.header(key,mime!=null&&key.equalsIgnoreCase("content-type")?mime:value));
        return HttpClient.newHttpClient().send(request.PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
