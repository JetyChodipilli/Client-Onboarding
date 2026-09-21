package com.brainserve.clientonboarding.organization.domain.repository;

import com.brainserve.clientonboarding.organization.domain.model.Organization;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationMember;
import com.brainserve.clientonboarding.organization.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrganizationAdminRepository {
    Optional<Organization> findOrganizationByIdAndTenant(UUID id, UUID tenantId);
    Optional<Organization> findOrganizationBySlug(String slug);
    Organization insertOrganization(Organization organization, UUID actorId);
    boolean updateOrganization(UUID id, UUID tenantId, String name, long version, UUID actorId, Instant now);
    List<String> listPermissionCodes();
    List<Role> listRoles(UUID organizationId);
    Optional<Role> findRole(UUID organizationId, UUID roleId);
    Role insertRole(Role role, UUID actorId, Instant now);
    boolean updateRole(UUID organizationId, UUID roleId, String name, String description,
                       Set<String> permissions, long version, UUID actorId, Instant now);
    boolean archiveRole(UUID organizationId, UUID roleId, long version, UUID actorId, Instant now);
    List<OrganizationMember> listMembers(UUID organizationId);
    Optional<OrganizationMember> findMember(UUID organizationId, UUID membershipId);
    OrganizationMember insertMember(OrganizationMember member, UUID actorId, Instant now);
    boolean updateMember(UUID organizationId, UUID membershipId, UUID roleId, String status,
                         long version, UUID actorId, Instant now);
    boolean activateMembership(UUID organizationId, UUID membershipId, UUID userId, Instant now);
    long countActiveManagers(UUID organizationId, String... requiredPermissions);
    long countActiveMembersForRole(UUID organizationId, UUID roleId);
}
