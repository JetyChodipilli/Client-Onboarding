package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingReviewAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OnboardingReviewHistoryResponse(
        UUID id,
        OnboardingReviewAction action,
        UUID reviewerUserId,
        String reason,
        List<UUID> revisionStepIds,
        long onboardingVersion,
        Instant createdAt) {
    public OnboardingReviewHistoryResponse {
        revisionStepIds = revisionStepIds == null ? List.of() : List.copyOf(revisionStepIds);
    }
}
