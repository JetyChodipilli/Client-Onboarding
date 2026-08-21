package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.time.Instant;
import java.util.UUID;

/** Feature-owned hook used by final review to reopen immutable/verified requirement state safely. */
public interface FinalReviewRevisionHandler {
    boolean supports(WorkflowStepType stepType);
    void reopen(RevisionContext context);

    record RevisionContext(
            UUID organizationId,
            UUID projectId,
            UUID onboardingId,
            UUID stepId,
            UUID reviewerUserId,
            String reason,
            Instant occurredAt) {}
}
