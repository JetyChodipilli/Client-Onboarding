package com.brainserve.onboarding.assets.infrastructure.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AssetObjectStorage {
    String bucket();
    PresignedOperation presignUpload(String objectKey, String contentType, long contentLength, Duration ttl, Map<String, String> metadata);
    StoredObject head(String bucket, String objectKey);
    void downloadTo(String bucket, String objectKey, Path destination);
    PresignedOperation presignDownload(String bucket, String objectKey, String filename, String contentType, Duration ttl);
    void delete(String bucket, String objectKey);
    boolean available();

    record PresignedOperation(String url, String method, Map<String, List<String>> headers, Instant expiresAt) {
        public PresignedOperation { headers = Map.copyOf(headers); }
    }
    record StoredObject(long sizeBytes, String eTag, String contentType, Map<String, String> metadata) {
        public StoredObject { metadata = Map.copyOf(metadata); }
    }
}
