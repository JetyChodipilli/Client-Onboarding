package com.brainserve.clientonboarding.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuthChallenge(
        UUID id,
        UUID organizationId,
        UUID userId,
        String tokenHash,
        Purpose purpose,
        String encryptedSecret,
        int attempts,
        Instant expiresAt,
        Instant consumedAt
) {
    public enum Purpose { MFA_VERIFY, MFA_ENROLL }
}
