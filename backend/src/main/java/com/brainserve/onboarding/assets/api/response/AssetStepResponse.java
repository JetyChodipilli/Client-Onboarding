package com.brainserve.onboarding.assets.api.response;

import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import java.util.List;
import java.util.UUID;

public record AssetStepResponse(
        UUID requirementId,
        UUID projectId,
        UUID onboardingId,
        UUID stepId,
        String name,
        String description,
        boolean required,
        boolean requiresReview,
        long maxFileSizeBytes,
        List<String> allowedMimeTypes,
        UUID assetId,
        AssetStatus status,
        UUID currentVersionId,
        long assetVersion,
        List<AssetVersionResponse> versions,
        List<AssetReviewResponse> reviews) {
    public AssetStepResponse {
        allowedMimeTypes = List.copyOf(allowedMimeTypes);
        versions = List.copyOf(versions);
        reviews = List.copyOf(reviews);
    }
}
