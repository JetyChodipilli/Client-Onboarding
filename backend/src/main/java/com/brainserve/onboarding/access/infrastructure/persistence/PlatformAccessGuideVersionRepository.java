package com.brainserve.onboarding.access.infrastructure.persistence;

import com.brainserve.onboarding.access.domain.model.PlatformAccessGuideVersion;
import com.brainserve.onboarding.access.domain.model.PlatformAccessGuideVersionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccessGuideVersionRepository extends JpaRepository<PlatformAccessGuideVersion, UUID> {
    Optional<PlatformAccessGuideVersion> findByOrganizationIdAndId(UUID organizationId, UUID id);
    List<PlatformAccessGuideVersion> findAllByOrganizationIdAndAccessTypeIdOrderByVersionNumberDesc(UUID organizationId, UUID accessTypeId);
    List<PlatformAccessGuideVersion> findAllByOrganizationIdAndAccessTypeIdIn(UUID organizationId, Collection<UUID> accessTypeIds);
    Optional<PlatformAccessGuideVersion> findFirstByOrganizationIdAndAccessTypeIdAndStatusOrderByVersionNumberDesc(
            UUID organizationId, UUID accessTypeId, PlatformAccessGuideVersionStatus status);
    boolean existsByOrganizationIdAndAccessTypeIdAndStatus(UUID organizationId, UUID accessTypeId, PlatformAccessGuideVersionStatus status);
    List<PlatformAccessGuideVersion> findAllByOrganizationIdAndStatusOrderByUpdatedAtDescIdDesc(UUID organizationId, PlatformAccessGuideVersionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PlatformAccessGuideVersion v where v.organizationId=:org and v.id=:id")
    Optional<PlatformAccessGuideVersion> findForUpdate(@Param("org") UUID organizationId, @Param("id") UUID id);
}
