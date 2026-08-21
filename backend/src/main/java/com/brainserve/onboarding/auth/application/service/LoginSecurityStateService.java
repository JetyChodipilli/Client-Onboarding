package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.common.security.SecurityProperties;
import com.brainserve.onboarding.identity.domain.model.UserAccount;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists authentication-failure security state independently from the request transaction.
 * Authentication endpoints intentionally fail with exceptions; failed-attempt counters must not
 * roll back with those exceptions or brute-force protection becomes ineffective.
 */
@Service
public class LoginSecurityStateService {
    private final IdentityAccountService users;
    private final SecurityProperties properties;
    private final Clock clock;

    public LoginSecurityStateService(IdentityAccountService users, SecurityProperties properties, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedPassword(UUID userId) {
        UserAccount user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null) return;
        user.recordFailedLogin(clock.instant(), properties.maxLoginAttempts(), properties.loginLockDuration());
        users.save(user);
    }
}
