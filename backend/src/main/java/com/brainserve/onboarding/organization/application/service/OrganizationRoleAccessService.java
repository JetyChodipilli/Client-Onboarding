package com.brainserve.onboarding.organization.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.organization.infrastructure.persistence.RoleRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-safe role lookup boundary for workflow assignment configuration. */
@Service
public class OrganizationRoleAccessService {
    private final RoleRepository roles;

    public OrganizationRoleAccessService(RoleRepository roles) { this.roles = roles; }

    @Transactional(readOnly = true)
    public void requireActiveRoles(UUID organizationId, Collection<UUID> roleIds) {
        Set<UUID> requested = roleIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (requested.isEmpty()) return;
        Set<UUID> found = roles.findAllByOrganizationIdAndStatusOrderByNameAsc(organizationId, "ACTIVE").stream()
                .map(role -> role.getId()).filter(requested::contains).collect(Collectors.toSet());
        if (!found.equals(requested)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNED_ROLE", "One or more assigned roles are unavailable in this organization.");
        }
    }
}
