package com.brainserve.onboarding.identity.application.service;

import com.brainserve.onboarding.identity.domain.model.UserAccount;
import com.brainserve.onboarding.identity.infrastructure.persistence.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Controlled application boundary for identity persistence used by sibling Phase 1 modules.
 * Business modules must not reach directly into identity persistence implementations.
 */
@Service
public class IdentityAccountService {
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;

    public IdentityAccountService(UserAccountRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findById(UUID userId) {
        return users.findById(userId);
    }

    public Optional<UserAccount> findByIdForUpdate(UUID userId) {
        return users.findByIdForUpdate(userId);
    }

    public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
        return users.findByNormalizedEmail(normalizedEmail);
    }

    /**
     * Serializes creation/lookup of a global identity for an email even when no user row exists yet.
     * PostgreSQL transaction advisory locks avoid a concurrent invitation-acceptance uniqueness race.
     */
    public void lockNormalizedEmail(String normalizedEmail) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, normalizedEmail);
    }

    public UserAccount save(UserAccount user) {
        return users.save(user);
    }

    public UserAccount saveAndFlush(UserAccount user) {
        return users.saveAndFlush(user);
    }
}
