package com.brainserve.clientonboarding.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(
        UUID id,
        UUID organizationId,
        UUID userId,
        String tokenHash,
        long credentialVersion,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant mfaVerifiedAt
) {
    public boolean isUsableAt(Instant now, Instant idleCutoff) {
        return revokedAt == null && expiresAt.isAfter(now) && lastSeenAt.isAfter(idleCutoff);
    }

    public boolean hasMfaAssurance() {
        return mfaVerifiedAt != null;
    }
}
