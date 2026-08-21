package com.brainserve.onboarding.assets.infrastructure.storage;

import com.brainserve.onboarding.common.error.ApiException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class DisabledAssetObjectStorage implements AssetObjectStorage {
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ASSET_STORAGE_UNAVAILABLE",
                "Secure object storage is not configured for this environment.");
    }
    @Override public String bucket() { return "unconfigured"; }
    @Override public PresignedOperation presignUpload(String objectKey, String contentType, long contentLength, Duration ttl, Map<String, String> metadata) { throw unavailable(); }
    @Override public StoredObject head(String bucket, String objectKey) { throw unavailable(); }
    @Override public void downloadTo(String bucket, String objectKey, Path destination) { throw unavailable(); }
    @Override public PresignedOperation presignDownload(String bucket, String objectKey, String filename, String contentType, Duration ttl) { throw unavailable(); }
    @Override public void delete(String bucket, String objectKey) { throw unavailable(); }
    @Override public boolean available() { return false; }
}
