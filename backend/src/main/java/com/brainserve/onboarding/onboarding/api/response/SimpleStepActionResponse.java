package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import java.util.UUID;

public record SimpleStepActionResponse(UUID stepId, OnboardingStepStatus status, boolean readyForReview) {}
