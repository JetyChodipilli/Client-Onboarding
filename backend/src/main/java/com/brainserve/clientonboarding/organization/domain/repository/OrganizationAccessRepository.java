package com.brainserve.clientonboarding.organization.domain.repository;

import com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationAccessRepository {
    Optional<OrganizationAccess> findByEmailAndSlug(String normalizedEmail, String normalizedSlug);
    Optional<OrganizationAccess> findByUserAndOrganization(UUID userId, UUID organizationId);
}
