package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.api.request.RequestOnboardingRevisionRequest;
import com.brainserve.onboarding.onboarding.api.response.FinalReviewChecklistResponse;
import com.brainserve.onboarding.onboarding.api.response.FinalReviewRequirementResponse;
import com.brainserve.onboarding.onboarding.api.response.OnboardingReviewHistoryResponse;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingReview;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingReviewAction;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.domain.policy.OnboardingReadinessPolicy;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingReviewRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.project.application.service.ProjectActivationService;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingFinalReviewService {
    private static final Set<WorkflowStepType> NON_REOPENABLE_FACTS = Set.of(
            WorkflowStepType.PAYMENT, WorkflowStepType.CONTRACT);

    private final OnboardingInstanceRepository onboardings;
    private final OnboardingStepInstanceRepository steps;
    private final OnboardingReviewRepository reviews;
    private final OnboardingStepCommandService stepCommands;
    private final ProjectActivationService projects;
    private final FinalReviewRevisionGate finalReviewRevisionGate;
    private final List<FinalReviewRevisionHandler> revisionHandlers;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OnboardingFinalReviewService(OnboardingInstanceRepository onboardings,
                                        OnboardingStepInstanceRepository steps,
                                        OnboardingReviewRepository reviews,
                                        OnboardingStepCommandService stepCommands,
                                        ProjectActivationService projects,
                                        FinalReviewRevisionGate finalReviewRevisionGate,
                                        List<FinalReviewRevisionHandler> revisionHandlers,
                                        ActivityTimelineService activity,
                                        AuditService audit,
                                        OutboxService outbox,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this.onboardings = onboardings;
        this.steps = steps;
        this.reviews = reviews;
        this.stepCommands = stepCommands;
        this.projects = projects;
        this.finalReviewRevisionGate = finalReviewRevisionGate;
        this.revisionHandlers = List.copyOf(revisionHandlers);
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinalReviewChecklistResponse checklist(TenantPrincipal principal, UUID onboardingId) {
        OnboardingInstance onboarding = onboardings.findByOrganizationIdAndId(principal.organizationId(), onboardingId)
                .orElseThrow(OnboardingFinalReviewService::notFound);
        return mapChecklist(principal.organizationId(), onboarding);
    }

    /** Narrow project-scoped review projection used by reviewers/activators who do not need full onboarding read access. */
    @Transactional(readOnly = true)
    public FinalReviewChecklistResponse checklistByProject(TenantPrincipal principal, UUID projectId) {
        OnboardingInstance onboarding = onboardings.findByOrganizationIdAndProjectId(principal.organizationId(), projectId)
                .orElseThrow(OnboardingFinalReviewService::notFound);
        return mapChecklist(principal.organizationId(), onboarding);
    }

    @Transactional
    public FinalReviewChecklistResponse startReview(TenantPrincipal principal, UUID onboardingId, long expectedVersion,
                                                    HttpServletRequest request) {
        OnboardingInstance onboarding = requireForUpdate(principal.organizationId(), onboardingId);
        requireVersion(onboarding, expectedVersion);
        List<OnboardingStepInstance> currentSteps = loadSteps(principal.organizationId(), onboardingId);
        requireReviewable(onboarding, currentSteps);

        List<OnboardingReview> history = reviews.findAllByOrganizationIdAndOnboardingIdOrderByCreatedAtDescIdDesc(
                principal.organizationId(), onboardingId);
        boolean alreadyStartedForVersion = !history.isEmpty()
                && history.get(0).getAction() == OnboardingReviewAction.REVIEW_STARTED
                && history.get(0).getOnboardingVersion() == onboarding.getVersion();
        if (!alreadyStartedForVersion) {
            Instant now = clock.instant();
            reviews.saveAndFlush(new OnboardingReview(UUID.randomUUID(), principal.organizationId(), onboardingId,
                    onboarding.getProjectId(), OnboardingReviewAction.REVIEW_STARTED, principal.userId(), null,
                    objectMapper.createArrayNode(), onboarding.getVersion(), now));
            var project = projects.require(principal.organizationId(), onboarding.getProjectId());
            activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(),
                    "ONBOARDING_REVIEW_STARTED", "ONBOARDING", onboardingId, "Final onboarding review started",
                    Map.of("onboardingVersion", onboarding.getVersion()));
            audit.record(principal.organizationId(), principal.userId(), "ONBOARDING_REVIEW_STARTED", "ONBOARDING",
                    onboardingId, null, Map.of("status", onboarding.getStatus(), "ready", true,
                            "version", onboarding.getVersion()), request);
        }
        return mapChecklist(principal.organizationId(), onboarding);
    }

    @Transactional
    public FinalReviewChecklistResponse approve(TenantPrincipal principal, UUID onboardingId, long expectedVersion,
                                                HttpServletRequest request) {
        OnboardingInstance onboarding = requireForUpdate(principal.organizationId(), onboardingId);
        requireVersion(onboarding, expectedVersion);
        List<OnboardingStepInstance> currentSteps = loadSteps(principal.organizationId(), onboardingId);
        requireApprovable(onboarding, currentSteps);
        Instant now = clock.instant();
        OnboardingStatus before = onboarding.getStatus();
        try {
            onboarding.transitionTo(OnboardingStatus.APPROVED, principal.userId(), now);
            onboarding.transitionTo(OnboardingStatus.COMPLETED, principal.userId(), now);
        } catch (IllegalStateException ex) {
            throw state(ex.getMessage());
        }
        onboardings.saveAndFlush(onboarding);
        ProjectActivationService.ProjectRef project = projects.markReadyFromCompletedOnboarding(
                principal.organizationId(), onboarding.getProjectId(), principal.userId());

        OnboardingReview review = reviews.saveAndFlush(new OnboardingReview(UUID.randomUUID(), principal.organizationId(),
                onboardingId, onboarding.getProjectId(), OnboardingReviewAction.APPROVED, principal.userId(), null,
                objectMapper.createArrayNode(), onboarding.getVersion(), now));
        activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(),
                "ONBOARDING_COMPLETED", "ONBOARDING", onboardingId, "Onboarding approved and completed",
                Map.of("reviewId", review.getId(), "projectStatus", project.status().name()));
        audit.record(principal.organizationId(), principal.userId(), "ONBOARDING_APPROVED", "ONBOARDING", onboardingId,
                Map.of("status", before, "version", expectedVersion),
                Map.of("status", onboarding.getStatus(), "ready", onboarding.isReady(), "version", onboarding.getVersion(),
                        "projectStatus", project.status()), request);
        outbox.record(principal.organizationId(), "ONBOARDING_COMPLETED", "ONBOARDING", onboardingId,
                Map.of("onboardingId", onboardingId, "projectId", project.id(), "reviewId", review.getId()));
        return mapChecklist(principal.organizationId(), onboarding);
    }

    @Transactional
    public FinalReviewChecklistResponse requestRevision(TenantPrincipal principal, UUID onboardingId,
                                                        RequestOnboardingRevisionRequest request,
                                                        HttpServletRequest servletRequest) {
        OnboardingInstance onboarding = requireForUpdate(principal.organizationId(), onboardingId);
        requireVersion(onboarding, request.version());
        List<OnboardingStepInstance> currentSteps = loadSteps(principal.organizationId(), onboardingId);
        requireReviewable(onboarding, currentSteps);

        LinkedHashSet<UUID> selectedIds = new LinkedHashSet<>(request.stepIds());
        if (selectedIds.size() != request.stepIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVISION_STEPS_DUPLICATED",
                    "Each revision requirement must be selected only once.");
        }
        Map<UUID, OnboardingStepInstance> byId = currentSteps.stream()
                .collect(Collectors.toMap(OnboardingStepInstance::getId, Function.identity()));
        List<OnboardingStepInstance> selected = selectedIds.stream().map(id -> {
            OnboardingStepInstance step = byId.get(id);
            if (step == null) throw notFound();
            if (!isRevisable(step)) {
                throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STEP_NOT_REOPENABLE",
                        "The selected requirement cannot be reopened from final review: " + step.getName());
            }
            return step;
        }).toList();

        Instant now = clock.instant();
        for (OnboardingStepInstance step : selected) {
            FinalReviewRevisionHandler handler = handlerFor(step.getStepType());
            if (handler != null) {
                try {
                    handler.reopen(new FinalReviewRevisionHandler.RevisionContext(
                            principal.organizationId(), onboarding.getProjectId(), onboardingId, step.getId(),
                            principal.userId(), request.reason(), now));
                } catch (IllegalStateException | IllegalArgumentException ex) {
                    throw state(ex.getMessage());
                }
            }
            stepCommands.transition(principal.organizationId(), onboardingId, step.getId(),
                    OnboardingStepStatus.NEEDS_REVISION, principal.userId(), WorkflowActorType.INTERNAL);
        }

        // A non-blocking revision keeps mathematical readiness=true, but the review decision must still return
        // the lifecycle to NEEDS_REVISION until the selected requirement is addressed.
        if (onboarding.getStatus() == OnboardingStatus.AWAITING_INTERNAL_REVIEW) {
            try {
                onboarding.transitionTo(OnboardingStatus.NEEDS_REVISION, principal.userId(), now);
            } catch (IllegalStateException ex) {
                throw state(ex.getMessage());
            }
            onboardings.saveAndFlush(onboarding);
        }

        ArrayNode revisionIds = objectMapper.createArrayNode();
        selectedIds.forEach(id -> revisionIds.add(id.toString()));
        OnboardingReview review = reviews.saveAndFlush(new OnboardingReview(UUID.randomUUID(), principal.organizationId(),
                onboardingId, onboarding.getProjectId(), OnboardingReviewAction.REVISION_REQUESTED, principal.userId(),
                request.reason(), revisionIds, onboarding.getVersion(), now));
        var project = projects.require(principal.organizationId(), onboarding.getProjectId());
        activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(),
                "ONBOARDING_REVISION_REQUESTED", "ONBOARDING", onboardingId, "Final review requested revisions",
                Map.of("reviewId", review.getId(), "stepIds", selectedIds, "reason", request.reason()));
        audit.record(principal.organizationId(), principal.userId(), "ONBOARDING_REVISION_REQUESTED", "ONBOARDING",
                onboardingId, Map.of("status", OnboardingStatus.AWAITING_INTERNAL_REVIEW, "version", request.version()),
                Map.of("status", onboarding.getStatus(), "ready", onboarding.isReady(), "version", onboarding.getVersion(),
                        "stepIds", selectedIds, "reason", request.reason()), servletRequest);
        outbox.record(principal.organizationId(), "ONBOARDING_REVISION_REQUESTED", "ONBOARDING", onboardingId,
                Map.of("onboardingId", onboardingId, "projectId", onboarding.getProjectId(), "reviewId", review.getId(),
                        "stepIds", selectedIds));
        return mapChecklist(principal.organizationId(), onboarding);
    }

    private FinalReviewChecklistResponse mapChecklist(UUID organizationId, OnboardingInstance onboarding) {
        List<OnboardingStepInstance> currentSteps = loadSteps(organizationId, onboarding.getId());
        boolean recomputedReady = isReady(currentSteps);
        var project = projects.require(organizationId, onboarding.getProjectId());
        int blockingTotal = (int) currentSteps.stream().filter(OnboardingStepInstance::isBlocking).count();
        int blockingCompleted = (int) currentSteps.stream().filter(OnboardingStepInstance::isBlocking)
                .filter(step -> step.getStatus() == OnboardingStepStatus.COMPLETED).count();
        int requiredTotal = (int) currentSteps.stream().filter(OnboardingStepInstance::isRequired).count();
        int requiredCompleted = (int) currentSteps.stream().filter(OnboardingStepInstance::isRequired)
                .filter(step -> step.getStatus() == OnboardingStepStatus.COMPLETED).count();
        List<FinalReviewRequirementResponse> requirements = currentSteps.stream().map(step ->
                new FinalReviewRequirementResponse(step.getId(), step.getStepKey(), step.getName(), step.getStepType(),
                        step.getDisplayOrder(), step.isRequired(), step.isBlocking(), step.isClientVisible(), step.getStatus(),
                        step.getStatus() == OnboardingStepStatus.COMPLETED, isRevisable(step))).toList();
        List<OnboardingReviewHistoryResponse> history = reviews
                .findAllByOrganizationIdAndOnboardingIdOrderByCreatedAtDescIdDesc(organizationId, onboarding.getId())
                .stream().map(this::mapReview).toList();
        boolean revisionOutstanding = finalReviewRevisionGate.hasOutstanding(organizationId, onboarding.getId(), currentSteps);
        boolean requiredComplete = currentSteps.stream()
                .filter(OnboardingStepInstance::isRequired)
                .allMatch(step -> step.getStatus() == OnboardingStepStatus.COMPLETED);
        boolean canApprove = recomputedReady && requiredComplete && !revisionOutstanding
                && onboarding.getStatus() == OnboardingStatus.AWAITING_INTERNAL_REVIEW
                && project.status() == ProjectStatus.ONBOARDING;
        return new FinalReviewChecklistResponse(onboarding.getId(), onboarding.getProjectId(), onboarding.getStatus(),
                project.status(), recomputedReady, blockingTotal, blockingCompleted, requiredTotal, requiredCompleted,
                canApprove, onboarding.getVersion(), project.version(), requirements, history);
    }

    private OnboardingReviewHistoryResponse mapReview(OnboardingReview review) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode node : review.getRevisionStepIds()) {
            ids.add(UUID.fromString(node.asText()));
        }
        return new OnboardingReviewHistoryResponse(review.getId(), review.getAction(), review.getReviewerUserId(),
                review.getReason(), ids, review.getOnboardingVersion(), review.getCreatedAt());
    }

    private void requireApprovable(OnboardingInstance onboarding, List<OnboardingStepInstance> currentSteps) {
        requireReviewable(onboarding, currentSteps);
        boolean requiredComplete = currentSteps.stream()
                .filter(OnboardingStepInstance::isRequired)
                .allMatch(step -> step.getStatus() == OnboardingStepStatus.COMPLETED);
        if (!requiredComplete) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_REQUIRED_REQUIREMENTS_INCOMPLETE",
                    "Every required onboarding requirement must be completed before final approval.");
        }
    }

    private void requireReviewable(OnboardingInstance onboarding, List<OnboardingStepInstance> currentSteps) {
        if (onboarding.getStatus() != OnboardingStatus.AWAITING_INTERNAL_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_NOT_READY_FOR_REVIEW",
                    "Final review is available only while onboarding is awaiting internal review.");
        }
        if (!isReady(currentSteps)) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_BLOCKERS_INCOMPLETE",
                    "All blocking requirements must be completed before final review.");
        }
        if (finalReviewRevisionGate.hasOutstanding(onboarding.getOrganizationId(), onboarding.getId(), currentSteps)) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_REVISION_OUTSTANDING",
                    "All requirements returned for revision must be completed before final review can continue.");
        }
        var project = projects.require(onboarding.getOrganizationId(), onboarding.getProjectId());
        if (project.status() != ProjectStatus.ONBOARDING) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_IN_ONBOARDING",
                    "Final review is available only while the project is in onboarding.");
        }
    }

    private boolean isRevisable(OnboardingStepInstance step) {
        return step.getStatus() == OnboardingStepStatus.COMPLETED
                && step.isAllowReopen()
                && !NON_REOPENABLE_FACTS.contains(step.getStepType())
                && handlerFor(step.getStepType()) != null;
    }

    private FinalReviewRevisionHandler handlerFor(WorkflowStepType type) {
        return revisionHandlers.stream().filter(handler -> handler.supports(type)).findFirst().orElse(null);
    }

    private List<OnboardingStepInstance> loadSteps(UUID organizationId, UUID onboardingId) {
        return steps.findAllByOrganizationIdAndOnboardingIdOrderByDisplayOrderAscIdAsc(organizationId, onboardingId);
    }

    private boolean isReady(List<OnboardingStepInstance> currentSteps) {
        return OnboardingReadinessPolicy.isReady(currentSteps.stream()
                .map(step -> new OnboardingReadinessPolicy.Requirement(step.isBlocking(), step.getStatus())).toList());
    }

    private OnboardingInstance requireForUpdate(UUID organizationId, UUID onboardingId) {
        return onboardings.findForUpdate(organizationId, onboardingId).orElseThrow(OnboardingFinalReviewService::notFound);
    }

    private static void requireVersion(OnboardingInstance onboarding, long expectedVersion) {
        if (onboarding.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The onboarding changed. Refresh and try again.");
        }
    }

    private static ApiException state(String message) {
        return new ApiException(HttpStatus.CONFLICT, "ONBOARDING_REVIEW_STATE_INVALID", message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested onboarding resource was not found.");
    }
}
