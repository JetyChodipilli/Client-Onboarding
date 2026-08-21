package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import java.util.List;
import java.util.UUID;

public record FinalReviewChecklistResponse(
        UUID onboardingId,
        UUID projectId,
        OnboardingStatus onboardingStatus,
        ProjectStatus projectStatus,
        boolean ready,
        int blockingTotal,
        int blockingCompleted,
        int requiredTotal,
        int requiredCompleted,
        boolean canApprove,
        long onboardingVersion,
        long projectVersion,
        List<FinalReviewRequirementResponse> requirements,
        List<OnboardingReviewHistoryResponse> history) {
    public FinalReviewChecklistResponse {
        requirements = List.copyOf(requirements);
        history = List.copyOf(history);
    }
}
