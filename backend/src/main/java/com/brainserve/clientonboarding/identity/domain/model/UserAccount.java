package com.brainserve.clientonboarding.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        PrincipalType principalType,
        UserStatus status,
        Instant emailVerifiedAt,
        int failedLoginCount,
        Instant lockedUntil,
        long credentialVersion,
        long version
) {
    public boolean isActiveAt(Instant now) {
        return status == UserStatus.ACTIVE
                && emailVerifiedAt != null
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    public enum PrincipalType { INTERNAL, CLIENT }

    public enum UserStatus { PENDING, ACTIVE, SUSPENDED, ARCHIVED }
}
