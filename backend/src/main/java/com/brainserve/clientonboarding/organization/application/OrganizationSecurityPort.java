package com.brainserve.clientonboarding.organization.application;

import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import java.time.Instant;
import java.util.UUID;

public interface OrganizationSecurityPort {
    void issueInvitation(UUID organizationId, UUID membershipId, UserAccount user,
                         String organizationName, Instant now);
    void revokeUserSessions(UUID userId, Instant now);
}
