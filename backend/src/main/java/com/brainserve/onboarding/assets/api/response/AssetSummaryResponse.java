package com.brainserve.onboarding.assets.api.response;

import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import java.time.Instant;
import java.util.UUID;

public record AssetSummaryResponse(UUID assetId, UUID projectId, UUID stepId, String requirementName,
                                   AssetStatus status, UUID currentVersionId, String currentFilename,
                                   String currentMimeType, Instant updatedAt, long version) {}
