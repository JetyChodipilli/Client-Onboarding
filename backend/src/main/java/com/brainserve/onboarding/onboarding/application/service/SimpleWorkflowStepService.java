package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.api.response.SimpleStepActionResponse;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled handler for workflow step types that do not have their own feature aggregate.
 * Form, asset, payment, contract, platform-access and manual-task steps remain owned by their
 * dedicated modules and can never be completed through this service.
 */
@Service
public class SimpleWorkflowStepService {
    private static final Set<WorkflowStepType> SIMPLE_CLIENT_TYPES = EnumSet.of(
            WorkflowStepType.WELCOME,
            WorkflowStepType.INSTRUCTION,
            WorkflowStepType.EXTERNAL_LINK,
            WorkflowStepType.VIDEO_GUIDE,
            WorkflowStepType.MEETING,
            WorkflowStepType.CUSTOM);
    private static final Set<WorkflowStepType> SIMPLE_INTERNAL_TYPES = EnumSet.of(
            WorkflowStepType.APPROVAL,
            WorkflowStepType.WELCOME,
            WorkflowStepType.INSTRUCTION,
            WorkflowStepType.EXTERNAL_LINK,
            WorkflowStepType.VIDEO_GUIDE,
            WorkflowStepType.MEETING,
            WorkflowStepType.CUSTOM);

    private final OnboardingStepInstanceRepository steps;
    private final OnboardingStepCommandService workflow;
    private final ClientPortalAccessService clientAccess;

    public SimpleWorkflowStepService(OnboardingStepInstanceRepository steps,
                                     OnboardingStepCommandService workflow,
                                     ClientPortalAccessService clientAccess) {
        this.steps = steps;
        this.workflow = workflow;
        this.clientAccess = clientAccess;
    }

    @Transactional
    public SimpleStepActionResponse completeClient(ClientPrincipal principal, UUID projectId, UUID stepId) {
        clientAccess.requireProjectActionAccess(principal.organizationId(), principal.userId(), projectId);
        OnboardingStepInstance step = steps.findByOrganizationIdAndId(principal.organizationId(), stepId)
                .orElseThrow(SimpleWorkflowStepService::notFound);
        var context = workflow.lockStepContext(principal.organizationId(), step.getOnboardingId(), stepId);
        if (!projectId.equals(context.projectId()) || !context.clientVisible()) throw notFound();
        if (!SIMPLE_CLIENT_TYPES.contains(context.stepType())) {
            throw new ApiException(HttpStatus.CONFLICT, "STEP_ACTION_NOT_GENERIC",
                    "This requirement must be completed through its dedicated workflow screen.");
        }
        if (!isClientActionState(context.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STEP_STATE_INVALID",
                    "This requirement is not currently available for client completion.");
        }

        OnboardingStepStatus current = context.status();
        if (current == OnboardingStepStatus.AVAILABLE || current == OnboardingStepStatus.NEEDS_REVISION) {
            workflow.transition(principal.organizationId(), step.getOnboardingId(), stepId,
                    OnboardingStepStatus.IN_PROGRESS, principal.userId(), WorkflowActorType.CLIENT);
            current = OnboardingStepStatus.IN_PROGRESS;
        }
        if (context.requiresReview()) {
            if (current == OnboardingStepStatus.IN_PROGRESS) {
                workflow.transition(principal.organizationId(), step.getOnboardingId(), stepId,
                        OnboardingStepStatus.SUBMITTED, principal.userId(), WorkflowActorType.CLIENT);
            }
            var submitted = workflow.lockStepContext(principal.organizationId(), step.getOnboardingId(), stepId);
            if (submitted.status() == OnboardingStepStatus.SUBMITTED) {
                var result = workflow.transition(principal.organizationId(), step.getOnboardingId(), stepId,
                        OnboardingStepStatus.UNDER_REVIEW, principal.userId(), WorkflowActorType.CLIENT);
                return new SimpleStepActionResponse(stepId, result.stepStatus(), result.ready());
            }
        } else if (current == OnboardingStepStatus.IN_PROGRESS) {
            var result = workflow.transition(principal.organizationId(), step.getOnboardingId(), stepId,
                    OnboardingStepStatus.COMPLETED, principal.userId(), WorkflowActorType.CLIENT);
            return new SimpleStepActionResponse(stepId, result.stepStatus(), result.ready());
        }
        var latest = workflow.lockStepContext(principal.organizationId(), step.getOnboardingId(), stepId);
        return new SimpleStepActionResponse(stepId, latest.status(), false);
    }

    @Transactional
    public SimpleStepActionResponse completeInternal(TenantPrincipal principal, UUID onboardingId, UUID stepId) {
        var context = workflow.lockStepContext(principal.organizationId(), onboardingId, stepId);
        if (!SIMPLE_INTERNAL_TYPES.contains(context.stepType())) {
            throw new ApiException(HttpStatus.CONFLICT, "STEP_ACTION_NOT_GENERIC",
                    "This requirement is owned by a dedicated feature module and cannot be manually completed here.");
        }
        OnboardingStepStatus current = context.status();
        if (current == OnboardingStepStatus.UNDER_REVIEW) {
            var result = workflow.transition(principal.organizationId(), onboardingId, stepId,
                    OnboardingStepStatus.COMPLETED, principal.userId(), WorkflowActorType.INTERNAL);
            return new SimpleStepActionResponse(stepId, result.stepStatus(), result.ready());
        }
        if (context.requiresReview() && context.clientVisible()) {
            throw new ApiException(HttpStatus.CONFLICT, "STEP_REVIEW_NOT_READY",
                    "This client-visible requirement must be submitted before internal approval.");
        }
        if (current == OnboardingStepStatus.AVAILABLE || current == OnboardingStepStatus.NEEDS_REVISION) {
            workflow.transition(principal.organizationId(), onboardingId, stepId,
                    OnboardingStepStatus.IN_PROGRESS, principal.userId(), WorkflowActorType.INTERNAL);
            current = OnboardingStepStatus.IN_PROGRESS;
        }
        if (current == OnboardingStepStatus.IN_PROGRESS && !context.requiresReview()) {
            var result = workflow.transition(principal.organizationId(), onboardingId, stepId,
                    OnboardingStepStatus.COMPLETED, principal.userId(), WorkflowActorType.INTERNAL);
            return new SimpleStepActionResponse(stepId, result.stepStatus(), result.ready());
        }
        if (context.requiresReview() && current == OnboardingStepStatus.IN_PROGRESS) {
            workflow.transition(principal.organizationId(), onboardingId, stepId,
                    OnboardingStepStatus.SUBMITTED, principal.userId(), WorkflowActorType.INTERNAL);
            current = OnboardingStepStatus.SUBMITTED;
        }
        if (context.requiresReview() && current == OnboardingStepStatus.SUBMITTED) {
            var result = workflow.transition(principal.organizationId(), onboardingId, stepId,
                    OnboardingStepStatus.UNDER_REVIEW, principal.userId(), WorkflowActorType.INTERNAL);
            return new SimpleStepActionResponse(stepId, result.stepStatus(), result.ready());
        }
        throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STEP_STATE_INVALID",
                "This requirement cannot be completed from its current state.");
    }

    private static boolean isClientActionState(OnboardingStepStatus status) {
        return status == OnboardingStepStatus.AVAILABLE
                || status == OnboardingStepStatus.IN_PROGRESS
                || status == OnboardingStepStatus.NEEDS_REVISION;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested onboarding requirement was not found.");
    }
}
