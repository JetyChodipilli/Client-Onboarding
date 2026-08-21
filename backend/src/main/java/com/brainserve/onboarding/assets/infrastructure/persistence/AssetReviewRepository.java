package com.brainserve.onboarding.assets.infrastructure.persistence;

import com.brainserve.onboarding.assets.domain.model.AssetReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetReviewRepository extends JpaRepository<AssetReview, UUID> {
    List<AssetReview> findAllByOrganizationIdAndAssetIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID assetId);
}
