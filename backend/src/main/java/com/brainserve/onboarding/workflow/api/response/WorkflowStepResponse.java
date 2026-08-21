package com.brainserve.onboarding.workflow.api.response;

import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record WorkflowStepResponse(
        UUID id, String stepKey, String name, String description, WorkflowStepType stepType, int displayOrder,
        boolean required, boolean blocking, boolean clientVisible, boolean requiresReview,
        DependencyMode dependencyMode, JsonNode conditionExpression, UUID assignedRoleId,
        Integer dueAfterHours, UUID reminderPolicyId, boolean allowSkip, boolean allowReopen,
        JsonNode configuration, List<String> dependencyKeys) {}
