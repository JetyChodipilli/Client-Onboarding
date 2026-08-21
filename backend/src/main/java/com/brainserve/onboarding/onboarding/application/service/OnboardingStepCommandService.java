package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstanceDependency;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.domain.policy.OnboardingReadinessPolicy;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceDependencyRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal workflow-engine command boundary for later step handlers. Phase 3 intentionally exposes no
 * generic HTTP endpoint that could bypass form/payment/contract/asset/access domain validation.
 */
@Service
public class OnboardingStepCommandService {
    private final OnboardingInstanceRepository onboardings;
    private final OnboardingStepInstanceRepository steps;
    private final OnboardingStepInstanceDependencyRepository dependencies;
    private final FinalReviewRevisionGate finalReviewRevisionGate;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final List<OnboardingStepTransitionListener> transitionListeners;
    private final Clock clock;

    public OnboardingStepCommandService(OnboardingInstanceRepository onboardings,
                                        OnboardingStepInstanceRepository steps,
                                        OnboardingStepInstanceDependencyRepository dependencies,
                                        FinalReviewRevisionGate finalReviewRevisionGate,
                                        ActivityTimelineService activity,
                                        AuditService audit,
                                        OutboxService outbox,
                                        List<OnboardingStepTransitionListener> transitionListeners,
                                        Clock clock) {
        this.onboardings = onboardings;
        this.steps = steps;
        this.dependencies = dependencies;
        this.finalReviewRevisionGate = finalReviewRevisionGate;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.transitionListeners = List.copyOf(transitionListeners);
        this.clock = clock;
    }

    /**
     * Acquires the canonical onboarding aggregate lock before a feature module locks its own requirement row.
     * This establishes onboarding -> feature-row lock ordering and avoids deadlocks with transition listeners.
     */
    @Transactional
    public LockedStep lockStepContext(UUID organizationId, UUID onboardingId, UUID stepId) {
        OnboardingInstance onboarding = onboardings.findForUpdate(organizationId, onboardingId)
                .orElseThrow(OnboardingStepCommandService::notFound);
        OnboardingStepInstance step = steps.findByOrganizationIdAndId(organizationId, stepId)
                .filter(value -> value.getOnboardingId().equals(onboardingId))
                .orElseThrow(OnboardingStepCommandService::notFound);
        return new LockedStep(onboarding.getProjectId(), step.getId(), step.getStepType(), step.getStatus(),
                step.isClientVisible(), step.isRequiresReview(), step.isAllowSkip(), step.isAllowReopen(), step.getVersion());
    }

    @Transactional
    public TransitionResult transition(UUID organizationId, UUID onboardingId, UUID stepId,
                                       OnboardingStepStatus target, UUID actorId) {
        return transition(organizationId, onboardingId, stepId, target, actorId, WorkflowActorType.INTERNAL);
    }

    @Transactional
    public TransitionResult transition(UUID organizationId, UUID onboardingId, UUID stepId,
                                       OnboardingStepStatus target, UUID actorId, WorkflowActorType actorType) {
        OnboardingInstance onboarding = onboardings.findForUpdate(organizationId, onboardingId)
                .orElseThrow(OnboardingStepCommandService::notFound);
        List<OnboardingStepInstance> values = steps.findAllByOrganizationIdAndOnboardingIdOrderByDisplayOrderAscIdAsc(organizationId, onboardingId);
        OnboardingStepInstance selected = values.stream().filter(value -> value.getId().equals(stepId)).findFirst()
                .orElseThrow(OnboardingStepCommandService::notFound);
        OnboardingStepStatus before = selected.getStatus();
        Instant now = clock.instant();
        try {
            selected.transitionTo(target, actorId, actorType.name(), now);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STEP_STATE_INVALID", ex.getMessage());
        }

        List<OnboardingStepInstanceDependency> edges = dependencies.findAllByOrganizationIdAndOnboardingId(organizationId, onboardingId);
        List<StepChange> changes = new java.util.ArrayList<>();
        changes.add(new StepChange(selected, before, selected.getStatus()));
        changes.addAll(unlockEligible(values, edges, actorId, actorType, now));
        steps.saveAllAndFlush(values);
        for (StepChange change : changes) {
            var transition = new OnboardingStepTransitionListener.StepTransition(organizationId, onboarding.getProjectId(), onboardingId,
                    change.step().getId(), change.step().getStepType(), change.before(), change.after(), actorId, actorType, now);
            transitionListeners.stream().filter(listener -> listener.supports(change.step().getStepType()))
                    .forEach(listener -> listener.onTransition(transition));
        }

        OnboardingStatus previousOnboardingStatus = onboarding.getStatus();
        if (previousOnboardingStatus == OnboardingStatus.NEEDS_REVISION
                && selected.getStatus() == OnboardingStepStatus.IN_PROGRESS) {
            try {
                onboarding.transitionTo(OnboardingStatus.IN_PROGRESS, actorId, actorType.name(), now);
            } catch (IllegalStateException ex) {
                throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STATE_INVALID", ex.getMessage());
            }
        }
        boolean ready = OnboardingReadinessPolicy.isReady(values.stream()
                .map(step -> new OnboardingReadinessPolicy.Requirement(step.isBlocking(), step.getStatus())).toList());
        boolean reviewRevisionOutstanding = finalReviewRevisionGate.hasOutstanding(organizationId, onboardingId, values);
        try {
            onboarding.updateReadiness(ready, reviewRevisionOutstanding, actorId, actorType.name(), now);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STATE_INVALID", ex.getMessage());
        }
        onboardings.saveAndFlush(onboarding);

        Map<String, String> beforeAudit = Map.of("stepKey", selected.getStepKey(), "status", before.name());
        Map<String, String> afterAudit = Map.of("stepKey", selected.getStepKey(), "status", selected.getStatus().name());
        if (actorType == WorkflowActorType.CLIENT) {
            activity.recordClient(organizationId, null, onboarding.getProjectId(), actorId, "ONBOARDING_STEP_STATE_CHANGED",
                    "ONBOARDING_STEP", stepId, "Workflow step state changed",
                    Map.of("stepKey", selected.getStepKey(), "from", before.name(), "to", selected.getStatus().name()));
        } else if (actorType == WorkflowActorType.SYSTEM) {
            activity.recordSystem(organizationId, null, onboarding.getProjectId(), "ONBOARDING_STEP_STATE_CHANGED",
                    "ONBOARDING_STEP", stepId, "Workflow step state changed",
                    Map.of("stepKey", selected.getStepKey(), "from", before.name(), "to", selected.getStatus().name()));
        } else {
            activity.record(organizationId, null, onboarding.getProjectId(), actorId, "ONBOARDING_STEP_STATE_CHANGED",
                    "ONBOARDING_STEP", stepId, "Workflow step state changed",
                    Map.of("stepKey", selected.getStepKey(), "from", before.name(), "to", selected.getStatus().name()));
        }
        audit.recordApplication(organizationId, actorId, actorType.name(), "ONBOARDING_STEP_STATE_CHANGED",
                "ONBOARDING_STEP", stepId, beforeAudit, afterAudit);
        if (previousOnboardingStatus != OnboardingStatus.AWAITING_INTERNAL_REVIEW
                && onboarding.getStatus() == OnboardingStatus.AWAITING_INTERNAL_REVIEW) {
            outbox.record(organizationId, "ONBOARDING_READY_FOR_REVIEW", "ONBOARDING", onboardingId,
                    Map.of("onboardingId", onboardingId, "projectId", onboarding.getProjectId()));
        }
        return new TransitionResult(selected.getId(), selected.getStatus(), onboarding.isReady(), previousOnboardingStatus,
                onboarding.getStatus(), onboarding.getVersion());
    }

    private static List<StepChange> unlockEligible(List<OnboardingStepInstance> values,
                                       List<OnboardingStepInstanceDependency> edges,
                                       UUID actorId, WorkflowActorType actorType, Instant now) {
        Map<UUID, OnboardingStepInstance> byId = values.stream()
                .collect(Collectors.toMap(OnboardingStepInstance::getId, value -> value));
        Map<UUID, List<UUID>> deps = edges.stream().collect(Collectors.groupingBy(
                OnboardingStepInstanceDependency::getStepInstanceId,
                Collectors.mapping(OnboardingStepInstanceDependency::getDependsOnStepInstanceId, Collectors.toList())));
        List<StepChange> changes = new java.util.ArrayList<>();
        boolean changed;
        do {
            changed = false;
            for (OnboardingStepInstance value : values) {
                if (value.getStatus() != OnboardingStepStatus.LOCKED) continue;
                List<UUID> required = deps.getOrDefault(value.getId(), List.of());
                boolean satisfied = switch (value.getDependencyMode()) {
                    case NONE -> true;
                    case ALL -> required.stream().allMatch(id -> completed(byId.get(id)));
                    case ANY -> required.stream().anyMatch(id -> completed(byId.get(id)));
                };
                if (satisfied) {
                    OnboardingStepStatus before = value.getStatus();
                    value.transitionTo(OnboardingStepStatus.AVAILABLE, actorId, actorType.name(), now);
                    changes.add(new StepChange(value, before, value.getStatus()));
                    changed = true;
                }
            }
        } while (changed);
        return changes;
    }

    private record StepChange(OnboardingStepInstance step, OnboardingStepStatus before, OnboardingStepStatus after) {}

    private static boolean completed(OnboardingStepInstance step) {
        return step != null && step.getStatus() == OnboardingStepStatus.COMPLETED;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested onboarding resource was not found.");
    }

    public record LockedStep(UUID projectId, UUID stepId, com.brainserve.onboarding.workflow.domain.model.WorkflowStepType stepType,
                             OnboardingStepStatus status, boolean clientVisible, boolean requiresReview, boolean allowSkip, boolean allowReopen, long version) {}

    public record TransitionResult(UUID stepId, OnboardingStepStatus stepStatus, boolean ready,
                                   OnboardingStatus onboardingStatusBefore, OnboardingStatus onboardingStatusAfter,
                                   long onboardingVersion) {}
}
