package com.brainserve.onboarding.assets.infrastructure.persistence;

import com.brainserve.onboarding.assets.domain.model.AssetVersion;
import com.brainserve.onboarding.assets.domain.model.AssetVersionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {
    Optional<AssetVersion> findByOrganizationIdAndId(UUID organizationId, UUID id);
    List<AssetVersion> findAllByOrganizationIdAndIdIn(UUID organizationId, java.util.Collection<UUID> ids);
    Optional<AssetVersion> findByOrganizationIdAndRequirementIdAndUploadIdempotencyKey(UUID organizationId, UUID requirementId, String uploadIdempotencyKey);
    List<AssetVersion> findAllByOrganizationIdAndAssetIdOrderByVersionNumberDesc(UUID organizationId, UUID assetId);
    Optional<AssetVersion> findFirstByOrganizationIdAndAssetIdOrderByVersionNumberDesc(UUID organizationId, UUID assetId);
    Optional<AssetVersion> findFirstByOrganizationIdAndAssetIdAndStatusOrderByVersionNumberDesc(UUID organizationId, UUID assetId, AssetVersionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AssetVersion v where v.organizationId=:organizationId and v.id=:versionId")
    Optional<AssetVersion> findForUpdate(UUID organizationId, UUID versionId);

    @Query("select v from AssetVersion v where v.status in :statuses and (v.nextScanAt is null or v.nextScanAt<=:now) order by v.createdAt asc, v.id asc")
    List<AssetVersion> findScanCandidates(Collection<AssetVersionStatus> statuses, Instant now, Pageable pageable);
}
