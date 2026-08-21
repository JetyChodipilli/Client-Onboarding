package com.brainserve.onboarding.assets.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.assets.scan")
public record AssetScanProperties(
        boolean enabled,
        String host,
        int port,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration pollDelay,
        int batchSize,
        Duration retryBaseDelay) {

    public AssetScanProperties {
        host = host == null || host.isBlank() ? "localhost" : host.trim();
        port = port <= 0 ? 3310 : port;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(2) : readTimeout;
        maxAttempts = maxAttempts <= 0 ? 5 : Math.min(maxAttempts, 20);
        pollDelay = pollDelay == null ? Duration.ofSeconds(10) : pollDelay;
        batchSize = batchSize <= 0 ? 10 : Math.min(batchSize, 100);
        retryBaseDelay = retryBaseDelay == null ? Duration.ofSeconds(30) : retryBaseDelay;
    }
}
