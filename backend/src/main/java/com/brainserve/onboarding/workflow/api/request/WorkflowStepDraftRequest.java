package com.brainserve.onboarding.workflow.api.request;

import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record WorkflowStepDraftRequest(
        @NotBlank @Size(max = 80) String stepKey,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 2000) String description,
        @NotNull WorkflowStepType stepType,
        @Min(0) int displayOrder,
        boolean required,
        boolean blocking,
        boolean clientVisible,
        boolean requiresReview,
        @NotNull DependencyMode dependencyMode,
        JsonNode conditionExpression,
        UUID assignedRoleId,
        @Min(0) @Max(8760) Integer dueAfterHours,
        UUID reminderPolicyId,
        boolean allowSkip,
        boolean allowReopen,
        JsonNode configuration,
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 80) String> dependencyKeys) {
}
