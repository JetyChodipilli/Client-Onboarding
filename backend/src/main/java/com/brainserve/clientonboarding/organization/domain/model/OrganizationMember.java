package com.brainserve.clientonboarding.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMember(
        UUID id,
        UUID organizationId,
        UUID userId,
        String email,
        String displayName,
        UUID roleId,
        String roleName,
        OrganizationAccess.MembershipStatus status,
        Instant invitedAt,
        Instant joinedAt,
        long version
) {
}
