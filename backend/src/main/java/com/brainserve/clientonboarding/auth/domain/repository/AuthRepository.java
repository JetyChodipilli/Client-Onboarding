package com.brainserve.clientonboarding.auth.domain.repository;

import com.brainserve.clientonboarding.auth.domain.model.AuthChallenge;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository {
    void insertSession(AuthSession session, String ipHash, String userAgentHash);
    Optional<AuthSession> findSessionByTokenHash(String tokenHash);
    void touchSession(UUID sessionId, Instant now);
    boolean consumeSession(UUID organizationId, UUID userId, UUID sessionId, Instant now, Instant idleCutoff);
    void revokeSession(UUID sessionId, Instant now);
    void revokeAllSessions(UUID userId, Instant now);
    void insertVerificationToken(UUID id, UUID organizationId, UUID userId, String tokenHash,
                                 Instant expiresAt, Instant now);
    Optional<SecurityToken> findVerificationToken(String tokenHash);
    boolean consumeVerificationToken(UUID id, Instant now);
    void insertPasswordResetToken(UUID id, UUID organizationId, UUID userId, String tokenHash,
                                  Instant expiresAt, Instant now);
    Optional<SecurityToken> findPasswordResetToken(String tokenHash);
    boolean consumePasswordResetToken(UUID id, Instant now);
    void insertOrganizationInvitation(UUID id, UUID organizationId, UUID membershipId, UUID userId,
                                      String tokenHash, Instant expiresAt, Instant now);
    Optional<OrganizationInvitation> findOrganizationInvitation(String tokenHash);
    void consumeOrganizationInvitation(UUID id, Instant now);
    Optional<MfaMethod> findMfaMethod(UUID userId);
    void saveMfaMethod(UUID userId, String encryptedSecret, long lastUsedStep, Instant now);
    boolean advanceMfaStep(UUID userId, Long previousStep, long newStep);
    void insertChallenge(AuthChallenge challenge, Instant now);
    Optional<AuthChallenge> findChallenge(String tokenHash);
    void incrementChallengeAttempts(UUID challengeId);
    void consumeChallenge(UUID challengeId, Instant now);
    void replaceRecoveryCodes(UUID userId, java.util.List<RecoveryCode> codes, Instant now);
    boolean consumeRecoveryCode(UUID userId, String codeHash, Instant now);

    record SecurityToken(UUID id, UUID organizationId, UUID userId, Instant expiresAt, Instant consumedAt) {
        public boolean usableAt(Instant now) {
            return consumedAt == null && expiresAt.isAfter(now);
        }
    }

    record MfaMethod(UUID id, UUID userId, String encryptedSecret, Long lastUsedStep) { }
    record RecoveryCode(UUID id, String hash) { }
    record OrganizationInvitation(UUID id, UUID organizationId, UUID membershipId, UUID userId,
                                  Instant expiresAt, Instant consumedAt) {
        public boolean usableAt(Instant now) {
            return consumedAt == null && expiresAt.isAfter(now);
        }
    }
}
