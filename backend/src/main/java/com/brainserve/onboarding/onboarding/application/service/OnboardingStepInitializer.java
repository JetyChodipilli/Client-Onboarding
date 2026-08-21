package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Feature extension invoked when a workflow step snapshot is created. Implementations may create feature-owned requirement snapshots. */
public interface OnboardingStepInitializer {
    boolean supports(WorkflowStepType stepType);
    void initialize(StepCreated step);

    record StepCreated(UUID organizationId, UUID projectId, UUID clientId, UUID onboardingId, UUID stepId,
                       WorkflowStepType stepType, String name, String description, OnboardingStepStatus status,
                       boolean clientVisible, boolean requiresReview, UUID assignedRoleId, Instant dueAt, UUID reminderPolicyId,
                       JsonNode configuration, UUID actorId, Instant occurredAt) {}
}
