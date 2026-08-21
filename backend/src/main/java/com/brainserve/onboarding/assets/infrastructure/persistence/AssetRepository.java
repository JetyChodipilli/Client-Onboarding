package com.brainserve.onboarding.assets.infrastructure.persistence;

import com.brainserve.onboarding.assets.domain.model.Asset;
import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<Asset> findByOrganizationIdAndRequirementId(UUID organizationId, UUID requirementId);
    List<Asset> findAllByOrganizationIdAndProjectIdOrderByUpdatedAtDescIdDesc(UUID organizationId, UUID projectId);
    Page<Asset> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    Page<Asset> findAllByOrganizationIdAndStatus(UUID organizationId, AssetStatus status, Pageable pageable);
    List<Asset> findAllByOrganizationIdAndStatusOrderByUpdatedAtAscIdAsc(UUID organizationId, AssetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.organizationId=:organizationId and a.id=:assetId")
    Optional<Asset> findForUpdate(UUID organizationId, UUID assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.organizationId=:organizationId and a.requirementId=:requirementId")
    Optional<Asset> findForUpdateByRequirement(UUID organizationId, UUID requirementId);
}
