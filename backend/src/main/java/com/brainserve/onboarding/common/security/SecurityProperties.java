package com.brainserve.onboarding.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        List<String> allowedOrigins,
        boolean publicApiDocs,
        String jwtSecretBase64,
        String mfaEncryptionKeyBase64,
        boolean requireExplicitSecrets,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        Duration emailVerificationTtl,
        Duration mfaChallengeTtl,
        Duration clientInvitationTtl,
        boolean secureCookies,
        int maxLoginAttempts,
        Duration loginLockDuration,
        String tokenIssuer,
        String tokenAudience) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("http://localhost:3000")
                : List.copyOf(allowedOrigins);
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(14) : refreshTokenTtl;
        passwordResetTtl = passwordResetTtl == null ? Duration.ofMinutes(30) : passwordResetTtl;
        emailVerificationTtl = emailVerificationTtl == null ? Duration.ofHours(24) : emailVerificationTtl;
        mfaChallengeTtl = mfaChallengeTtl == null ? Duration.ofMinutes(5) : mfaChallengeTtl;
        clientInvitationTtl = clientInvitationTtl == null ? Duration.ofHours(72) : clientInvitationTtl;
        maxLoginAttempts = maxLoginAttempts <= 0 ? 5 : maxLoginAttempts;
        loginLockDuration = loginLockDuration == null ? Duration.ofMinutes(15) : loginLockDuration;
        tokenIssuer = tokenIssuer == null || tokenIssuer.isBlank() ? "client-onboarding-platform" : tokenIssuer;
        tokenAudience = tokenAudience == null || tokenAudience.isBlank() ? "client-onboarding-api" : tokenAudience;
    }
}
