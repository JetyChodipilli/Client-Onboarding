package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.time.Instant;
import java.util.UUID;

public record ClientPortalStepResponse(UUID id, String stepKey, String name, String description, WorkflowStepType stepType,
        OnboardingStepStatus status, boolean required, boolean blocking, boolean requiresReview,
        Instant dueAt, String lockedReason, String actionLabel) {}
