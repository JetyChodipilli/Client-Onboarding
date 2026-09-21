package com.brainserve.clientonboarding.auth.infrastructure.integration;

import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.application.SecurityNotificationPort;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.organization.application.OrganizationSecurityPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuthOrganizationSecurityAdapter implements OrganizationSecurityPort {
    private final AuthRepository auth;
    private final SecureTokenService tokens;
    private final SecurityNotificationPort notifications;
    private final AuthProperties properties;

    public AuthOrganizationSecurityAdapter(AuthRepository auth, SecureTokenService tokens,
                                           SecurityNotificationPort notifications, AuthProperties properties) {
        this.auth = auth;
        this.tokens = tokens;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Override
    public void issueInvitation(UUID organizationId, UUID membershipId, UserAccount user,
                                String organizationName, Instant now) {
        String raw = tokens.issue();
        auth.insertOrganizationInvitation(UUID.randomUUID(), organizationId, membershipId, user.id(),
                tokens.hash(raw), now.plus(properties.tokenDuration()), now);
        notifications.sendOrganizationInvitation(user.email(), user.displayName(), organizationName,
                properties.publicAppUrl() + "/accept-invitation?token="
                        + URLEncoder.encode(raw, StandardCharsets.UTF_8));
    }

    @Override
    public void revokeUserSessions(UUID userId, Instant now) {
        auth.revokeAllSessions(userId, now);
    }
}
