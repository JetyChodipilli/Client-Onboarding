package com.brainserve.onboarding.organization.infrastructure.persistence;

import com.brainserve.onboarding.organization.domain.model.OrganizationMembership;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    Optional<OrganizationMembership> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Page<OrganizationMembership> findAllByOrganizationId(UUID organizationId, Pageable pageable);
}
