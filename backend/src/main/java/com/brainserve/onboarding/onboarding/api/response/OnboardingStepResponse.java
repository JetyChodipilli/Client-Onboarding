package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OnboardingStepResponse(
        UUID id,
        String stepKey,
        String name,
        String description,
        WorkflowStepType stepType,
        int displayOrder,
        boolean required,
        boolean blocking,
        boolean clientVisible,
        boolean requiresReview,
        DependencyMode dependencyMode,
        List<String> dependencyKeys,
        JsonNode conditionExpression,
        UUID assignedRoleId,
        Instant dueAt,
        UUID reminderPolicyId,
        boolean allowSkip,
        boolean allowReopen,
        JsonNode configuration,
        OnboardingStepStatus status,
        Instant completedAt,
        long version) {
    public OnboardingStepResponse { dependencyKeys = List.copyOf(dependencyKeys); }
}
