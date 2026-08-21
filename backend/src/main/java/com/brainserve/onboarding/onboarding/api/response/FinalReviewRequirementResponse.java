package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.UUID;

public record FinalReviewRequirementResponse(
        UUID stepId,
        String stepKey,
        String name,
        WorkflowStepType stepType,
        int displayOrder,
        boolean required,
        boolean blocking,
        boolean clientVisible,
        OnboardingStepStatus status,
        boolean satisfied,
        boolean revisable) {}
