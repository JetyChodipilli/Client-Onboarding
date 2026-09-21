package com.brainserve.clientonboarding.auth.infrastructure.configuration;

import java.time.Duration;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AuthProperties(
        java.util.List<String> corsAllowedOrigins,
        String publicAppUrl,
        boolean sessionCookieSecure,
        Duration sessionDuration,
        Duration sessionIdleTimeout,
        int loginMaxAttempts,
        Duration loginLockDuration,
        Duration tokenDuration,
        String mfaEncryptionKey,
        String mailFrom,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        boolean smtpStarttls,
        boolean smtpAuth,
        Bootstrap bootstrap
) {
    public AuthProperties {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
                || corsAllowedOrigins.stream().anyMatch(origin -> !validHttpOrigin(origin))) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must contain exact HTTP(S) origins");
        }
        if (!validHttpUrl(publicAppUrl)) {
            throw new IllegalArgumentException("PUBLIC_APP_URL must be an absolute HTTP(S) URL");
        }
        if (!positive(sessionDuration) || !positive(sessionIdleTimeout)
                || sessionIdleTimeout.compareTo(sessionDuration) > 0
                || !positive(loginLockDuration) || !positive(tokenDuration)) {
            throw new IllegalArgumentException("Security durations must be positive and idle timeout must not exceed session duration");
        }
        if (loginMaxAttempts < 1) throw new IllegalArgumentException("LOGIN_MAX_ATTEMPTS must be positive");
        if (mailFrom == null || mailFrom.isBlank() || smtpHost == null || smtpHost.isBlank()
                || smtpPort < 1 || smtpPort > 65_535) {
            throw new IllegalArgumentException("Security mail transport configuration is invalid");
        }
        if (smtpAuth && (smtpUsername == null || smtpUsername.isBlank()
                || smtpPassword == null || smtpPassword.isBlank())) {
            throw new IllegalArgumentException("SMTP credentials are required when SMTP_AUTH is enabled");
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    private static boolean validHttpOrigin(String value) {
        if (!validHttpUrl(value) || "*".equals(value)) return false;
        URI uri = URI.create(value);
        return uri.getPath().isEmpty() && uri.getQuery() == null && uri.getFragment() == null;
    }

    private static boolean validHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public record Bootstrap(
            boolean enabled,
            String organizationName,
            String organizationSlug,
            String adminName,
            String adminEmail,
            String adminPassword
    ) { }
}
