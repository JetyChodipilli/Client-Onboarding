package com.brainserve.onboarding.access.infrastructure.persistence;

import com.brainserve.onboarding.access.domain.model.PlatformAccessType;
import com.brainserve.onboarding.access.domain.model.PlatformAccessTypeStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccessTypeRepository extends JpaRepository<PlatformAccessType, UUID> {
    Optional<PlatformAccessType> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<PlatformAccessType> findByOrganizationIdAndCode(UUID organizationId, String code);
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
    Page<PlatformAccessType> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    Page<PlatformAccessType> findAllByOrganizationIdAndStatus(UUID organizationId, PlatformAccessTypeStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PlatformAccessType t where t.organizationId=:org and t.id=:id")
    Optional<PlatformAccessType> findForUpdate(@Param("org") UUID organizationId, @Param("id") UUID id);
}
