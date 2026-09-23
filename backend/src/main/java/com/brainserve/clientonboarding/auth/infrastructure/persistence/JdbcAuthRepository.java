package com.brainserve.clientonboarding.auth.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.auth.domain.model.AuthChallenge;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuthRepository implements AuthRepository {
    private final JdbcClient jdbc;

    public JdbcAuthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertSession(AuthSession session, String ipHash, String userAgentHash) {
        jdbc.sql("""
                INSERT INTO auth_sessions (id, organization_id, user_id, token_hash, credential_version,
                    created_at, last_seen_at, expires_at, revoked_at, mfa_verified_at, ip_hash, user_agent_hash)
                VALUES (:id, :organizationId, :userId, :tokenHash, :credentialVersion, :createdAt,
                    :lastSeenAt, :expiresAt, NULL, :mfaVerifiedAt, :ipHash, :userAgentHash)
                """).param("id", session.id()).param("organizationId", session.organizationId())
                .param("userId", session.userId()).param("tokenHash", session.tokenHash())
                .param("credentialVersion", session.credentialVersion()).param("createdAt", timestamp(session.createdAt()))
                .param("lastSeenAt", timestamp(session.lastSeenAt())).param("expiresAt", timestamp(session.expiresAt()))
                .param("mfaVerifiedAt", timestamp(session.mfaVerifiedAt()))
                .param("ipHash", ipHash).param("userAgentHash", userAgentHash).update();
    }

    @Override
    public Optional<AuthSession> findSessionByTokenHash(String tokenHash) {
        return jdbc.sql("SELECT * FROM auth_sessions WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash).query(this::mapSession).optional();
    }

    @Override
    public void touchSession(UUID sessionId, Instant now) {
        jdbc.sql("UPDATE auth_sessions SET last_seen_at = :now WHERE id = :id AND revoked_at IS NULL")
                .param("now", timestamp(now)).param("id", sessionId).update();
    }

    @Override
    public boolean consumeSession(UUID organizationId, UUID userId, UUID sessionId, Instant now, Instant idleCutoff) {
        return jdbc.sql("""
                UPDATE auth_sessions SET revoked_at = :now
                WHERE id = :id AND organization_id = :org AND user_id = :user
                  AND revoked_at IS NULL AND expires_at > :now AND last_seen_at > :cutoff
                  AND credential_version = (SELECT credential_version FROM users WHERE id = :user)
                """).param("id", sessionId).param("org", organizationId).param("user", userId)
                .param("now", timestamp(now)).param("cutoff", timestamp(idleCutoff)).update() == 1;
    }

    @Override
    public void revokeSession(UUID sessionId, Instant now) {
        jdbc.sql("UPDATE auth_sessions SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL")
                .param("now", timestamp(now)).param("id", sessionId).update();
    }

    @Override
    public void revokeAllSessions(UUID userId, Instant now) {
        jdbc.sql("UPDATE auth_sessions SET revoked_at = :now WHERE user_id = :userId AND revoked_at IS NULL")
                .param("now", timestamp(now)).param("userId", userId).update();
    }

    @Override
    public void insertVerificationToken(UUID id, UUID organizationId, UUID userId, String tokenHash,
                                        Instant expiresAt, Instant now) {
        jdbc.sql("UPDATE email_verification_tokens SET consumed_at = :now WHERE user_id = :userId AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("userId", userId).update();
        jdbc.sql("""
                INSERT INTO email_verification_tokens
                    (id, organization_id, user_id, token_hash, expires_at, consumed_at, created_at)
                VALUES (:id, :organizationId, :userId, :tokenHash, :expiresAt, NULL, :now)
                """).param("id", id).param("organizationId", organizationId).param("userId", userId)
                .param("tokenHash", tokenHash).param("expiresAt", timestamp(expiresAt))
                .param("now", timestamp(now)).update();
    }

    @Override
    public Optional<SecurityToken> findVerificationToken(String tokenHash) {
        return securityToken("email_verification_tokens", tokenHash);
    }

    @Override
    public boolean consumeVerificationToken(UUID id, Instant now) {
        return jdbc.sql("UPDATE email_verification_tokens SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("id", id).update() == 1;
    }

    @Override
    public void insertPasswordResetToken(UUID id, UUID organizationId, UUID userId, String tokenHash,
                                         Instant expiresAt, Instant now) {
        jdbc.sql("UPDATE password_reset_tokens SET consumed_at = :now WHERE user_id = :userId AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("userId", userId).update();
        jdbc.sql("""
                INSERT INTO password_reset_tokens
                    (id, organization_id, user_id, token_hash, expires_at, consumed_at, created_at)
                VALUES (:id, :organizationId, :userId, :tokenHash, :expiresAt, NULL, :now)
                """).param("id", id).param("organizationId", organizationId).param("userId", userId)
                .param("tokenHash", tokenHash).param("expiresAt", timestamp(expiresAt))
                .param("now", timestamp(now)).update();
    }

    @Override
    public Optional<SecurityToken> findPasswordResetToken(String tokenHash) {
        return securityToken("password_reset_tokens", tokenHash);
    }

    @Override
    public boolean consumePasswordResetToken(UUID id, Instant now) {
        return jdbc.sql("UPDATE password_reset_tokens SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("id", id).update() == 1;
    }

    @Override
    public void insertOrganizationInvitation(UUID id, UUID organizationId, UUID membershipId, UUID userId,
                                             String tokenHash, Instant expiresAt, Instant now) {
        jdbc.sql("""
                UPDATE organization_invitation_tokens SET consumed_at = :now
                WHERE membership_id = :membershipId AND consumed_at IS NULL
                """).param("now", timestamp(now)).param("membershipId", membershipId).update();
        jdbc.sql("""
                INSERT INTO organization_invitation_tokens
                    (id, organization_id, membership_id, user_id, token_hash, expires_at, consumed_at, created_at)
                VALUES (:id, :organizationId, :membershipId, :userId, :tokenHash, :expiresAt, NULL, :now)
                """).param("id", id).param("organizationId", organizationId).param("membershipId", membershipId)
                .param("userId", userId).param("tokenHash", tokenHash).param("expiresAt", timestamp(expiresAt))
                .param("now", timestamp(now)).update();
    }

    @Override
    public Optional<OrganizationInvitation> findOrganizationInvitation(String tokenHash) {
        return jdbc.sql("""
                SELECT id, organization_id, membership_id, user_id, expires_at, consumed_at
                FROM organization_invitation_tokens WHERE token_hash = :tokenHash
                """).param("tokenHash", tokenHash).query((rs, rowNum) -> new OrganizationInvitation(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getObject("membership_id", UUID.class), rs.getObject("user_id", UUID.class),
                        instant(rs, "expires_at"), instant(rs, "consumed_at"))).optional();
    }

    @Override
    public void consumeOrganizationInvitation(UUID id, Instant now) {
        jdbc.sql("UPDATE organization_invitation_tokens SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("id", id).update();
    }

    @Override
    public Optional<MfaMethod> findMfaMethod(UUID userId) {
        return jdbc.sql("SELECT id, user_id, encrypted_secret, last_used_step FROM mfa_methods WHERE user_id = :userId")
                .param("userId", userId).query((rs, rowNum) -> new MfaMethod(
                        rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("encrypted_secret"), (Long) rs.getObject("last_used_step"))).optional();
    }

    @Override
    public void saveMfaMethod(UUID userId, String encryptedSecret, long lastUsedStep, Instant now) {
        jdbc.sql("""
                INSERT INTO mfa_methods (id, user_id, encrypted_secret, key_version, enrolled_at, enabled_at, last_used_step)
                VALUES (:id, :userId, :secret, 1, :now, :now, :step)
                """).param("id", UUID.randomUUID()).param("userId", userId).param("secret", encryptedSecret)
                .param("now", timestamp(now)).param("step", lastUsedStep).update();
    }

    @Override
    public boolean advanceMfaStep(UUID userId, Long previousStep, long newStep) {
        String predicate = previousStep == null ? "last_used_step IS NULL" : "last_used_step = :previous";
        var statement = jdbc.sql("UPDATE mfa_methods SET last_used_step = :newStep WHERE user_id = :userId AND " + predicate)
                .param("newStep", newStep).param("userId", userId);
        if (previousStep != null) statement = statement.param("previous", previousStep);
        return statement.update() == 1;
    }

    @Override
    public void insertChallenge(AuthChallenge challenge, Instant now) {
        jdbc.sql("UPDATE auth_challenges SET consumed_at = :now WHERE user_id = :userId AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("userId", challenge.userId()).update();
        jdbc.sql("""
                INSERT INTO auth_challenges (id, organization_id, user_id, token_hash, purpose,
                    encrypted_secret, attempts, expires_at, consumed_at, created_at)
                VALUES (:id, :organizationId, :userId, :tokenHash, :purpose, :secret,
                    :attempts, :expiresAt, NULL, :now)
                """).param("id", challenge.id()).param("organizationId", challenge.organizationId())
                .param("userId", challenge.userId()).param("tokenHash", challenge.tokenHash())
                .param("purpose", challenge.purpose().name()).param("secret", challenge.encryptedSecret())
                .param("attempts", challenge.attempts()).param("expiresAt", timestamp(challenge.expiresAt()))
                .param("now", timestamp(now)).update();
    }

    @Override
    public Optional<AuthChallenge> findChallenge(String tokenHash) {
        return jdbc.sql("SELECT * FROM auth_challenges WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash).query((rs, rowNum) -> new AuthChallenge(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getString("token_hash"),
                        AuthChallenge.Purpose.valueOf(rs.getString("purpose")), rs.getString("encrypted_secret"),
                        rs.getInt("attempts"), instant(rs, "expires_at"), instant(rs, "consumed_at"))).optional();
    }

    @Override
    public void incrementChallengeAttempts(UUID challengeId) {
        jdbc.sql("UPDATE auth_challenges SET attempts = attempts + 1 WHERE id = :id AND consumed_at IS NULL")
                .param("id", challengeId).update();
    }

    @Override
    public void consumeChallenge(UUID challengeId, Instant now) {
        jdbc.sql("UPDATE auth_challenges SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
                .param("now", timestamp(now)).param("id", challengeId).update();
    }

    @Override
    @Transactional
    public void replaceRecoveryCodes(UUID userId, List<RecoveryCode> codes, Instant now) {
        jdbc.sql("DELETE FROM mfa_recovery_codes WHERE user_id = :userId")
                .param("userId", userId).update();
        for (RecoveryCode code : codes) {
            jdbc.sql("""
                    INSERT INTO mfa_recovery_codes (id, user_id, code_hash, created_at, consumed_at)
                    VALUES (:id, :userId, :hash, :now, NULL)
                    """).param("id", code.id()).param("userId", userId).param("hash", code.hash())
                    .param("now", timestamp(now)).update();
        }
    }

    @Override
    public boolean consumeRecoveryCode(UUID userId, String codeHash, Instant now) {
        return jdbc.sql("""
                UPDATE mfa_recovery_codes SET consumed_at = :now
                WHERE user_id = :userId AND code_hash = :hash AND consumed_at IS NULL
                """).param("now", timestamp(now)).param("userId", userId).param("hash", codeHash).update() == 1;
    }

    private Optional<SecurityToken> securityToken(String table, String tokenHash) {
        if (!table.equals("email_verification_tokens") && !table.equals("password_reset_tokens")) {
            throw new IllegalArgumentException("Unexpected security token table");
        }
        return jdbc.sql("SELECT id, organization_id, user_id, expires_at, consumed_at FROM " + table
                        + " WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash).query((rs, rowNum) -> new SecurityToken(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getObject("user_id", UUID.class), instant(rs, "expires_at"),
                        instant(rs, "consumed_at"))).optional();
    }

    private AuthSession mapSession(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuthSession(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("token_hash"),
                rs.getLong("credential_version"), instant(rs, "created_at"), instant(rs, "last_seen_at"),
                instant(rs, "expires_at"), instant(rs, "revoked_at"), instant(rs, "mfa_verified_at"));
    }
}
