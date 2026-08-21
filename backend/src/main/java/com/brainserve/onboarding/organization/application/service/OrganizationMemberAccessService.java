package com.brainserve.onboarding.organization.application.service;

import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationMembershipRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Narrow application boundary for validating an internal organization membership from sibling modules. */
@Service
public class OrganizationMemberAccessService {
    private final OrganizationMembershipRepository memberships;

    public OrganizationMemberAccessService(OrganizationMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public boolean isActiveMembership(UUID organizationId, UUID membershipId) {
        return memberships.findByOrganizationIdAndId(organizationId, membershipId)
                .map(membership -> "ACTIVE".equals(membership.getStatus()))
                .orElse(false);
    }
}
