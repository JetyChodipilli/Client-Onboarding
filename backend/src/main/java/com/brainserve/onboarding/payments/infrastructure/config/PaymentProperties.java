package com.brainserve.onboarding.payments.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.payments")
public record PaymentProperties(String provider, String webhookSecretBase64, String checkoutBaseUrl,
                                Duration webhookMaxAge, Duration sessionTtl, int maxWebhookBytes) {
    public PaymentProperties {
        provider = provider == null || provider.isBlank() ? "DISABLED" : provider.trim().toUpperCase(java.util.Locale.ROOT);
        checkoutBaseUrl = checkoutBaseUrl == null || checkoutBaseUrl.isBlank() ? "http://localhost:3000/portal/payments/sandbox" : checkoutBaseUrl.trim();
        webhookMaxAge = webhookMaxAge == null ? Duration.ofMinutes(5) : webhookMaxAge;
        sessionTtl = sessionTtl == null ? Duration.ofMinutes(30) : sessionTtl;
        maxWebhookBytes = maxWebhookBytes <= 0 ? 262_144 : Math.min(maxWebhookBytes, 1_048_576);
    }
}
