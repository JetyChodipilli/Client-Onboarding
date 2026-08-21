package com.brainserve.onboarding.assets.api.response;

import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetDetailResponse(
        UUID assetId,
        UUID projectId,
        UUID onboardingId,
        UUID stepId,
        UUID formSubmissionId,
        UUID formFieldId,
        String requirementName,
        String description,
        boolean required,
        boolean requiresReview,
        long maxFileSizeBytes,
        List<String> allowedMimeTypes,
        AssetStatus status,
        UUID currentVersionId,
        long version,
        Instant updatedAt,
        List<AssetVersionResponse> versions,
        List<AssetReviewResponse> reviews) {
    public AssetDetailResponse {
        allowedMimeTypes = List.copyOf(allowedMimeTypes);
        versions = List.copyOf(versions);
        reviews = List.copyOf(reviews);
    }
}
