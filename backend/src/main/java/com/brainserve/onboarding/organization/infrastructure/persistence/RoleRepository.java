package com.brainserve.onboarding.organization.infrastructure.persistence;

import com.brainserve.onboarding.organization.domain.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findAllByOrganizationIdAndStatusOrderByNameAsc(UUID organizationId, String status);
    Optional<Role> findByOrganizationIdAndIdAndStatus(UUID organizationId, UUID id, String status);
    Optional<Role> findByOrganizationIdAndCode(UUID organizationId, String code);
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
