package com.brainserve.onboarding.assets.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.assets.storage")
public record AssetStorageProperties(
        boolean enabled,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl) {

    public AssetStorageProperties {
        region = blank(region) ? "us-east-1" : region.trim();
        bucket = blank(bucket) ? "client-onboarding-assets" : bucket.trim();
        uploadUrlTtl = uploadUrlTtl == null ? Duration.ofMinutes(15) : uploadUrlTtl;
        downloadUrlTtl = downloadUrlTtl == null ? Duration.ofMinutes(10) : downloadUrlTtl;
        if (enabled) {
            if (blank(endpoint) || blank(accessKey) || blank(secretKey)) {
                throw new IllegalStateException("Enabled asset storage requires endpoint, access key, and secret key");
            }
            if (uploadUrlTtl.isNegative() || uploadUrlTtl.isZero() || uploadUrlTtl.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException("Asset upload URL TTL must be between 1 second and 1 hour");
            }
            if (downloadUrlTtl.isNegative() || downloadUrlTtl.isZero() || downloadUrlTtl.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException("Asset download URL TTL must be between 1 second and 1 hour");
            }
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
