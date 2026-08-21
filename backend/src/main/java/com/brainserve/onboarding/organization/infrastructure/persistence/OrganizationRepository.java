package com.brainserve.onboarding.organization.infrastructure.persistence;

import com.brainserve.onboarding.organization.domain.model.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
}
