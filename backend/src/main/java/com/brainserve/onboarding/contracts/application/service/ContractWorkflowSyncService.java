package com.brainserve.onboarding.contracts.application.service;

import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.application.service.WorkflowActorType;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Completes a CONTRACT workflow requirement only after the contract module reports a verified signed document. */
@Service
public class ContractWorkflowSyncService {
    private final OnboardingStepAccessService access;
    private final OnboardingStepCommandService commands;

    public ContractWorkflowSyncService(OnboardingStepAccessService access, OnboardingStepCommandService commands) {
        this.access = access; this.commands = commands;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSigned(ContractSignedEvent event) {
        if (event.onboardingId() == null || event.stepId() == null) return;
        for (int guard = 0; guard < 5; guard++) {
            var step = access.require(event.organizationId(), event.stepId());
            OnboardingStepStatus status = step.status();
            if (status == OnboardingStepStatus.COMPLETED) return;
            OnboardingStepStatus target = next(status, step.requiresReview());
            if (target == null) return;
            commands.transition(event.organizationId(), event.onboardingId(), event.stepId(), target, null, WorkflowActorType.SYSTEM);
        }
        throw new IllegalStateException("Contract workflow step did not reach COMPLETED within the bounded transition sequence");
    }

    private static OnboardingStepStatus next(OnboardingStepStatus current, boolean requiresReview) {
        return switch (current) {
            case AVAILABLE -> requiresReview ? OnboardingStepStatus.IN_PROGRESS : OnboardingStepStatus.COMPLETED;
            case IN_PROGRESS -> requiresReview ? OnboardingStepStatus.SUBMITTED : OnboardingStepStatus.COMPLETED;
            case SUBMITTED -> requiresReview ? OnboardingStepStatus.UNDER_REVIEW : OnboardingStepStatus.COMPLETED;
            case UNDER_REVIEW -> OnboardingStepStatus.COMPLETED;
            case NEEDS_REVISION, FAILED -> OnboardingStepStatus.IN_PROGRESS;
            case COMPLETED -> null;
            case LOCKED, SKIPPED, CANCELLED -> null;
        };
    }
}
