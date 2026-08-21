package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.access.domain.model.PlatformAccessRequest;
import com.brainserve.onboarding.access.domain.model.PlatformAccessRequestStatus;
import com.brainserve.onboarding.access.domain.model.PlatformAccessReview;
import com.brainserve.onboarding.access.domain.model.PlatformAccessReviewAction;
import com.brainserve.onboarding.access.infrastructure.persistence.PlatformAccessRequestRepository;
import com.brainserve.onboarding.access.infrastructure.persistence.PlatformAccessReviewRepository;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.onboarding.application.service.FinalReviewRevisionHandler;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlatformAccessFinalReviewRevisionHandler implements FinalReviewRevisionHandler {
    private final PlatformAccessRequestRepository requests;
    private final PlatformAccessReviewRepository reviews;
    private final OutboxService outbox;

    public PlatformAccessFinalReviewRevisionHandler(PlatformAccessRequestRepository requests,
                                                    PlatformAccessReviewRepository reviews,
                                                    OutboxService outbox) {
        this.requests = requests;
        this.reviews = reviews;
        this.outbox = outbox;
    }

    @Override public boolean supports(WorkflowStepType stepType) { return stepType == WorkflowStepType.PLATFORM_ACCESS; }

    @Override
    public void reopen(RevisionContext context) {
        PlatformAccessRequest request = requests.findByStepForUpdate(context.organizationId(), context.stepId())
                .filter(value -> value.getStatus() == PlatformAccessRequestStatus.VERIFIED)
                .orElseThrow(() -> new IllegalStateException("The platform access requirement is not verified and cannot be reopened."));
        request.requestRevision(context.reviewerUserId(), context.occurredAt());
        requests.saveAndFlush(request);
        reviews.saveAndFlush(new PlatformAccessReview(UUID.randomUUID(), context.organizationId(), request.getId(),
                context.projectId(), PlatformAccessReviewAction.REQUEST_REVISION, context.reason(),
                context.reviewerUserId(), context.occurredAt()));
        outbox.record(context.organizationId(), "ACCESS_REVISION_REQUESTED", "PLATFORM_ACCESS_REQUEST", request.getId(),
                Map.of("requestId", request.getId(), "stepId", context.stepId(), "projectId", context.projectId(),
                        "source", "FINAL_REVIEW"));
    }
}
