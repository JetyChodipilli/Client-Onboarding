package com.brainserve.onboarding.assets.infrastructure.storage;

import com.brainserve.onboarding.assets.infrastructure.config.AssetStorageProperties;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class S3AssetObjectStorage implements AssetObjectStorage {
    private final AssetStorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;
    private final Clock clock;

    public S3AssetObjectStorage(AssetStorageProperties properties, S3Client client, S3Presigner presigner, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.presigner = presigner;
        this.clock = clock;
    }

    @Override public String bucket() { return properties.bucket(); }
    @Override public boolean available() { return true; }

    @Override
    public PresignedOperation presignUpload(String objectKey, String contentType, long contentLength, Duration ttl, Map<String, String> metadata) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .metadata(metadata)
                .build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build());
        return new PresignedOperation(signed.url().toString(), "PUT", signed.signedHeaders(), clock.instant().plus(ttl));
    }

    @Override
    public StoredObject head(String bucket, String objectKey) {
        try {
            var result = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return new StoredObject(result.contentLength(), result.eTag(), result.contentType(), result.metadata());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) throw new ObjectMissingException("Object was not found", ex);
            throw ex;
        }
    }

    @Override
    public void downloadTo(String bucket, String objectKey, Path destination) {
        client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build(), ResponseTransformer.toFile(destination));
    }

    @Override
    public PresignedOperation presignDownload(String bucket, String objectKey, String filename, String contentType, Duration ttl) {
        String disposition = "attachment; filename=\"" + filename.replace("\"", "") + "\"";
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseContentDisposition(disposition)
                .responseContentType(contentType)
                .build();
        var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build());
        return new PresignedOperation(signed.url().toString(), "GET", signed.signedHeaders(), clock.instant().plus(ttl));
    }

    @Override
    public void delete(String bucket, String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }
}
