package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.time.Instant;
import java.util.UUID;

/** Feature extension notified after a server-validated workflow-step state transition. */
public interface OnboardingStepTransitionListener {
    boolean supports(WorkflowStepType stepType);
    void onTransition(StepTransition transition);

    record StepTransition(UUID organizationId, UUID projectId, UUID onboardingId, UUID stepId,
                          WorkflowStepType stepType, OnboardingStepStatus before, OnboardingStepStatus after,
                          UUID actorId, WorkflowActorType actorType, Instant occurredAt) {}
}
