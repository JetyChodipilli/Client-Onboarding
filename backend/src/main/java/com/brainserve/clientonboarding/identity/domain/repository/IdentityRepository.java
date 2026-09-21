package com.brainserve.clientonboarding.identity.domain.repository;

import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {
    Optional<UserAccount> findById(UUID id);
    Optional<UserAccount> findByEmail(String normalizedEmail);
    void insert(UserAccount user, Instant now, UUID actorId);
    void recordFailedLogin(UUID userId, int maximumAttempts, Instant lockedUntil, Instant now);
    void clearFailedLogins(UUID userId, Instant now);
    void verifyAndSetPassword(UUID userId, String passwordHash, Instant now);
    void resetPassword(UUID userId, String passwordHash, Instant now);
}
