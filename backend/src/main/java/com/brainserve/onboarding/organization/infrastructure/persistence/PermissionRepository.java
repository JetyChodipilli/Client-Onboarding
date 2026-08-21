package com.brainserve.onboarding.organization.infrastructure.persistence;

import com.brainserve.onboarding.organization.domain.model.Permission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrderByCategoryAscCodeAsc();
}
