package com.brainserve.onboarding.assets.api.response;

import com.brainserve.onboarding.assets.domain.model.AssetReviewAction;
import java.time.Instant;
import java.util.UUID;

public record AssetReviewResponse(UUID id, UUID assetVersionId, UUID reviewerUserId, AssetReviewAction action,
                                  String note, Instant createdAt) {}
