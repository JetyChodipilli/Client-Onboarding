package com.brainserve.clientonboarding.identity.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.identity.domain.repository.IdentityRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {
    private final JdbcClient jdbc;

    public JdbcIdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return jdbc.sql("SELECT * FROM users WHERE id = :id")
                .param("id", id).query(this::map).optional();
    }

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        return jdbc.sql("SELECT * FROM users WHERE email = :email")
                .param("email", normalizedEmail).query(this::map).optional();
    }

    @Override
    public void insert(UserAccount user, Instant now, UUID actorId) {
        jdbc.sql("""
                INSERT INTO users (id, email, display_name, password_hash, principal_type, status,
                    email_verified_at, failed_login_count, locked_until, credential_version,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :email, :name, :password, :type, :status, :verified, :failures,
                    :locked, :credentialVersion, :now, :actor, :now, :actor, :version)
                """)
                .param("id", user.id()).param("email", user.email()).param("name", user.displayName())
                .param("password", user.passwordHash()).param("type", user.principalType().name())
                .param("status", user.status().name()).param("verified", timestamp(user.emailVerifiedAt()))
                .param("failures", user.failedLoginCount()).param("locked", timestamp(user.lockedUntil()))
                .param("credentialVersion", user.credentialVersion()).param("now", timestamp(now))
                .param("actor", actorId).param("version", user.version()).update();
    }

    @Override
    public void recordFailedLogin(UUID userId, int maximumAttempts, Instant lockedUntil, Instant now) {
        jdbc.sql("""
                UPDATE users SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE WHEN failed_login_count + 1 >= :maximumAttempts
                        THEN :locked ELSE locked_until END,
                    updated_at = :now, version = version + 1 WHERE id = :id
                """).param("maximumAttempts", maximumAttempts).param("locked", timestamp(lockedUntil))
                .param("now", timestamp(now)).param("id", userId).update();
    }

    @Override
    public void clearFailedLogins(UUID userId, Instant now) {
        jdbc.sql("""
                UPDATE users SET failed_login_count = 0, locked_until = NULL,
                    updated_at = :now, version = version + 1 WHERE id = :id
                """).param("now", timestamp(now)).param("id", userId).update();
    }

    @Override
    public void verifyAndSetPassword(UUID userId, String passwordHash, Instant now) {
        jdbc.sql("""
                UPDATE users SET password_hash = :password, status = 'ACTIVE',
                    email_verified_at = COALESCE(email_verified_at, :now), credential_version = credential_version + 1,
                    failed_login_count = 0, locked_until = NULL, updated_at = :now, version = version + 1
                WHERE id = :id AND status <> 'ARCHIVED'
                """).param("password", passwordHash).param("now", timestamp(now)).param("id", userId).update();
    }

    @Override
    public void resetPassword(UUID userId, String passwordHash, Instant now) {
        jdbc.sql("""
                UPDATE users SET password_hash = :password, credential_version = credential_version + 1,
                    failed_login_count = 0, locked_until = NULL, updated_at = :now, version = version + 1
                WHERE id = :id AND status = 'ACTIVE'
                """).param("password", passwordHash).param("now", timestamp(now)).param("id", userId).update();
    }

    private UserAccount map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UserAccount(
                rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                rs.getString("password_hash"), UserAccount.PrincipalType.valueOf(rs.getString("principal_type")),
                UserAccount.UserStatus.valueOf(rs.getString("status")), instant(rs, "email_verified_at"),
                rs.getInt("failed_login_count"), instant(rs, "locked_until"),
                rs.getLong("credential_version"), rs.getLong("version"));
    }
}
