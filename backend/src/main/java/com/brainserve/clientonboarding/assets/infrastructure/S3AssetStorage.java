package com.brainserve.clientonboarding.assets.infrastructure;

import com.brainserve.clientonboarding.assets.application.AssetStorage;
import com.brainserve.clientonboarding.assets.application.AssetErrors;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

@Component
public class S3AssetStorage implements AssetStorage {
    private final AssetProperties settings;
    private final S3Client client;
    private final S3Presigner signer;
    public S3AssetStorage(AssetProperties settings) {
        this.settings=settings; settings.validate();
        if(!settings.isEnabled()) { client=null;signer=null;return; }
        var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.getAccessKey(),settings.getSecretKey()));
        var config=S3Configuration.builder().pathStyleAccessEnabled(true).checksumValidationEnabled(false).build();
        client=S3Client.builder().region(Region.of(settings.getRegion())).credentialsProvider(credentials).endpointOverride(URI.create(settings.getEndpoint()))
                .serviceConfiguration(config).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(45)).apiCallAttemptTimeout(Duration.ofSeconds(35))).build();
        signer=S3Presigner.builder().region(Region.of(settings.getRegion())).credentialsProvider(credentials).endpointOverride(URI.create(settings.getPublicEndpoint())).serviceConfiguration(config).build();
    }
    private void requireStorage() {
        if(client==null) throw AssetErrors.unavailable();
        try {
            if(client.getBucketVersioning(b->b.bucket(settings.getBucket())).status()!=BucketVersioningStatus.ENABLED) throw AssetErrors.unavailable();
        } catch(software.amazon.awssdk.core.exception.SdkException e) { throw AssetErrors.unavailable(); }
    }
    public SignedUrl upload(String key,String mime,long size,String sha256) {
        requireStorage();
        var signed=signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(PutObjectRequest.builder().bucket(settings.getBucket()).key(key).contentType(mime).contentLength(size).metadata(Map.of("sha256",sha256)).build()).build());
        // Content-Length is supplied automatically by the browser, but must remain signed.
        if(!signed.signedHeaders().containsKey("content-length")) throw new IllegalStateException("Storage signer did not bind upload length");
        return new SignedUrl(signed.url().toString(),"PUT",signed.signedHeaders().entrySet().stream().filter(e->!java.util.Set.of("host","content-length").contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey,e->String.join(",",e.getValue()))),Instant.now().plusSeconds(600));
    }
    public StoredObject inspect(String key,String versionId) {
        requireStorage();
        try {
            var h=client.headObject(HeadObjectRequest.builder().bucket(settings.getBucket()).key(key).versionId(versionId).build());
            if(h.versionId()==null || h.versionId().equals("null")) throw AssetErrors.unavailable();
            return new StoredObject(h.versionId(),h.contentLength(),h.contentType(),h.metadata().get("sha256"),h.lastModified());
        } catch(software.amazon.awssdk.core.exception.SdkException e) { throw AssetErrors.unavailable(); }
    }
    public InputStream read(String key,String versionId) {
        requireStorage();
        try { return client.getObject(GetObjectRequest.builder().bucket(settings.getBucket()).key(key).versionId(versionId).build()); }
        catch(software.amazon.awssdk.core.exception.SdkException e) { throw AssetErrors.unavailable(); }
    }
    public SignedUrl download(String key,String versionId,String filename) {
        requireStorage();
        var signed=signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofSeconds(60))
                .getObjectRequest(GetObjectRequest.builder().bucket(settings.getBucket()).key(key).versionId(versionId)
                        .responseContentType("application/octet-stream").responseContentDisposition("attachment; filename=\""+filename+"\"").responseCacheControl("no-store").build()).build());
        return new SignedUrl(signed.url().toString(),"GET",Map.of(),Instant.now().plusSeconds(60));
    }
    @PreDestroy void close() { if(client!=null)client.close();if(signer!=null)signer.close(); }
}
