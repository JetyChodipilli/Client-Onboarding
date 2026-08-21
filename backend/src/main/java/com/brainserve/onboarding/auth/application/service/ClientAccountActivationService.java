package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
import com.brainserve.onboarding.identity.domain.model.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates or proves a global identity, then links it to one client/project without creating an internal membership. */
@Service
public class ClientAccountActivationService {
    private final IdentityAccountService users;
    private final ClientPortalAccessService portalAccess;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginSecurityStateService loginSecurityState;
    private final Clock clock;

    public ClientAccountActivationService(IdentityAccountService users,
                                          ClientPortalAccessService portalAccess,
                                          PasswordEncoder passwordEncoder,
                                          PasswordPolicy passwordPolicy,
                                          LoginSecurityStateService loginSecurityState,
                                          Clock clock) {
        this.users = users;
        this.portalAccess = portalAccess;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.loginSecurityState = loginSecurityState;
        this.clock = clock;
    }

    @Transactional
    public ActivationResult activate(UUID organizationId, UUID clientId, UUID projectId,
                                     String email, String displayName, ClientProjectAccessLevel accessLevel,
                                     UUID invitedBy, String password) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        users.lockNormalizedEmail(normalizedEmail);
        Instant now = clock.instant();
        UserAccount user = users.findByNormalizedEmail(normalizedEmail).orElse(null);
        boolean created = false;
        if (user == null) {
            passwordPolicy.validate(password, email);
            user = new UserAccount(UUID.randomUUID(), email, displayName, passwordEncoder.encode(password), now, true);
            users.saveAndFlush(user);
            created = true;
        } else {
            if (user.isLocked(now) || !"ACTIVE".equals(user.getStatus())) throw invalidCredentials();
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                loginSecurityState.recordFailedPassword(user.getId());
                throw invalidCredentials();
            }
            user.recordSuccessfulLogin(now);
            if (user.getEmailVerifiedAt() == null) user.markEmailVerified(now);
            users.saveAndFlush(user);
        }

        var grant = portalAccess.grantProjectAccess(
                organizationId, clientId, projectId, user.getId(), accessLevel, invitedBy, now);
        return new ActivationResult(user, grant.clientUserId(), grant.projectAccessId(), created);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVITATION_CREDENTIALS_INVALID",
                "Use the password for your existing account, or choose a password for a new account.");
    }

    public record ActivationResult(UserAccount user, UUID clientUserId, UUID projectAccessId, boolean newIdentity) {}
}
