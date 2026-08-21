package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OnboardingResponse(
        UUID id,
        UUID projectId,
        UUID templateId,
        UUID templateVersionId,
        String templateName,
        int templateVersionNumber,
        OnboardingStatus status,
        boolean ready,
        int progressPercent,
        long completedApplicableSteps,
        long totalApplicableSteps,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt,
        long version,
        List<OnboardingStepResponse> steps) {
    public OnboardingResponse { steps = List.copyOf(steps); }
}
