package com.brainserve.onboarding.organization.application.service;

import com.brainserve.onboarding.organization.domain.model.Organization;
import com.brainserve.onboarding.organization.domain.model.OrganizationInvitation;
import com.brainserve.onboarding.organization.domain.model.OrganizationMembership;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationInvitationRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationMembershipRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.RbacJdbcRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Controlled application boundary for tenant identity and RBAC state used by authentication. */
@Service
public class OrganizationIdentityService {
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationInvitationRepository invitations;
    private final RbacJdbcRepository rbac;

    public OrganizationIdentityService(OrganizationRepository organizations,
                                       OrganizationMembershipRepository memberships,
                                       OrganizationInvitationRepository invitations,
                                       RbacJdbcRepository rbac) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.invitations = invitations;
        this.rbac = rbac;
    }

    public Optional<Organization> findOrganizationById(UUID organizationId) {
        return organizations.findById(organizationId);
    }

    public Optional<Organization> findOrganizationBySlug(String slug) {
        return organizations.findBySlugIgnoreCase(slug);
    }

    public Optional<OrganizationMembership> findMembership(UUID organizationId, UUID membershipId) {
        return memberships.findByOrganizationIdAndId(organizationId, membershipId);
    }

    public Optional<OrganizationMembership> findMembershipForUser(UUID organizationId, UUID userId) {
        return memberships.findByOrganizationIdAndUserId(organizationId, userId);
    }

    public OrganizationMembership saveMembership(OrganizationMembership membership) {
        return memberships.save(membership);
    }

    public void flushMemberships() {
        memberships.flush();
    }

    public Optional<OrganizationInvitation> findInvitationByTokenHashForUpdate(String tokenHash) {
        return invitations.findByTokenHashForUpdate(tokenHash);
    }

    public OrganizationInvitation saveInvitation(OrganizationInvitation invitation) {
        return invitations.save(invitation);
    }

    public List<UUID> invitationRoleIds(UUID organizationId, UUID invitationId) {
        return rbac.invitationRoleIds(organizationId, invitationId);
    }

    public void replaceMembershipRoles(UUID organizationId, UUID membershipId, Set<UUID> roleIds, UUID actorId) {
        rbac.replaceMembershipRoles(organizationId, membershipId, roleIds, actorId);
    }

    public Set<String> permissionCodes(UUID organizationId, UUID membershipId) {
        return rbac.permissionCodes(organizationId, membershipId);
    }
}
