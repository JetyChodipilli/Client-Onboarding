package com.brainserve.onboarding.access.infrastructure.persistence;

import com.brainserve.onboarding.access.domain.model.PlatformAccessRequest;
import com.brainserve.onboarding.access.domain.model.PlatformAccessRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccessRequestRepository extends JpaRepository<PlatformAccessRequest, UUID> {
    Optional<PlatformAccessRequest> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<PlatformAccessRequest> findByOrganizationIdAndStepInstanceId(UUID organizationId, UUID stepInstanceId);
    Optional<PlatformAccessRequest> findByOrganizationIdAndProjectIdAndStepInstanceId(UUID organizationId, UUID projectId, UUID stepInstanceId);
    Page<PlatformAccessRequest> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    Page<PlatformAccessRequest> findAllByOrganizationIdAndStatus(UUID organizationId, PlatformAccessRequestStatus status, Pageable pageable);
    Page<PlatformAccessRequest> findAllByOrganizationIdAndProjectId(UUID organizationId, UUID projectId, Pageable pageable);
    Page<PlatformAccessRequest> findAllByOrganizationIdAndProjectIdAndStatus(UUID organizationId, UUID projectId, PlatformAccessRequestStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlatformAccessRequest r where r.organizationId=:org and r.id=:id")
    Optional<PlatformAccessRequest> findForUpdate(@Param("org") UUID organizationId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlatformAccessRequest r where r.organizationId=:org and r.stepInstanceId=:stepId")
    Optional<PlatformAccessRequest> findByStepForUpdate(@Param("org") UUID organizationId, @Param("stepId") UUID stepInstanceId);
}
