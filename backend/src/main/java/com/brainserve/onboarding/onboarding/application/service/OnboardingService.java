package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientLookupService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.onboarding.api.request.StartOnboardingRequest;
import com.brainserve.onboarding.onboarding.api.response.OnboardingResponse;
import com.brainserve.onboarding.onboarding.api.response.OnboardingStepResponse;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstanceDependency;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.domain.policy.OnboardingReadinessPolicy;
import com.brainserve.onboarding.onboarding.domain.policy.OnboardingProgressPolicy;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceDependencyRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.project.application.service.ProjectWorkflowAccessService;
import com.brainserve.onboarding.servicecatalog.application.service.ServiceCatalogLookupService;
import com.brainserve.onboarding.workflow.application.service.WorkflowConditionEngine;
import com.brainserve.onboarding.workflow.application.service.WorkflowSnapshotService;
import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class OnboardingService {
    private final OnboardingInstanceRepository onboardings;
    private final OnboardingStepInstanceRepository steps;
    private final OnboardingStepInstanceDependencyRepository dependencies;
    private final WorkflowSnapshotService workflowSnapshots;
    private final WorkflowConditionEngine conditions;
    private final ProjectWorkflowAccessService projectAccess;
    private final ClientLookupService clientLookup;
    private final ServiceCatalogLookupService serviceLookup;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final List<OnboardingStepInitializer> stepInitializers;
    private final Clock clock;

    public OnboardingService(OnboardingInstanceRepository onboardings,
                             OnboardingStepInstanceRepository steps,
                             OnboardingStepInstanceDependencyRepository dependencies,
                             WorkflowSnapshotService workflowSnapshots,
                             WorkflowConditionEngine conditions,
                             ProjectWorkflowAccessService projectAccess,
                             ClientLookupService clientLookup,
                             ServiceCatalogLookupService serviceLookup,
                             ActivityTimelineService activity,
                             AuditService audit,
                             OutboxService outbox,
                             List<OnboardingStepInitializer> stepInitializers,
                             Clock clock) {
        this.onboardings = onboardings;
        this.steps = steps;
        this.dependencies = dependencies;
        this.workflowSnapshots = workflowSnapshots;
        this.conditions = conditions;
        this.projectAccess = projectAccess;
        this.clientLookup = clientLookup;
        this.serviceLookup = serviceLookup;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.stepInitializers = List.copyOf(stepInitializers);
        this.clock = clock;
    }

    @Transactional
    public OnboardingResponse start(TenantPrincipal principal, UUID projectId, String idempotencyKey,
                                    StartOnboardingRequest request, HttpServletRequest servletRequest) {
        String keyHash = CryptoSupport.sha256Hex(principal.organizationId() + ":" + idempotencyKey);
        String requestHash = CryptoSupport.sha256Hex(projectId + ":" + request.templateVersionId() + ":" + request.projectVersion());
        var existing = onboardings.findByOrganizationIdAndStartIdempotencyKeyHash(principal.organizationId(), keyHash);
        if (existing.isPresent()) return requireSameRequest(principal.organizationId(), existing.get(), requestHash);

        var projectBefore = projectAccess.require(principal.organizationId(), projectId);
        if (projectBefore.version() != request.projectVersion()) throw versionConflict();
        var client = clientLookup.requireUsableForRelationshipWrite(principal.organizationId(), projectBefore.clientId());
        var service = serviceLookup.requireActiveForRelationshipWrite(principal.organizationId(), projectBefore.serviceId());
        var template = workflowSnapshots.requirePublished(principal.organizationId(), request.templateVersionId());

        var context = new WorkflowConditionEngine.ConditionContext(Map.of(
                "service.code", service.code(),
                "service.id", service.id().toString(),
                "client.status", client.status().name(),
                "client.id", client.id().toString(),
                "project.status", projectBefore.status().name(),
                "project.id", projectBefore.id().toString()));
        List<WorkflowSnapshotService.StepSnapshot> applicable = template.steps().stream()
                .filter(step -> conditions.evaluate(step.conditionExpression(), context))
                .toList();
        validateApplicableDependencies(applicable);

        ProjectWorkflowAccessService.ProjectRef project;
        try {
            project = projectAccess.transitionToOnboarding(principal.organizationId(), projectId, request.projectVersion(),
                    principal.userId(), clock.instant());
        } catch (ApiException ex) {
            if ("PROJECT_STATE_INVALID".equals(ex.code())) {
                var raced = onboardings.findByOrganizationIdAndStartIdempotencyKeyHash(principal.organizationId(), keyHash);
                if (raced.isPresent()) return requireSameRequest(principal.organizationId(), raced.get(), requestHash);
                if (onboardings.findByOrganizationIdAndProjectId(principal.organizationId(), projectId).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_EXISTS", "This project already has an onboarding instance.");
                }
            }
            throw ex;
        }

        Instant now = clock.instant();
        OnboardingInstance instance = onboardings.saveAndFlush(new OnboardingInstance(
                UUID.randomUUID(), principal.organizationId(), projectId, template.templateId(), template.versionId(),
                template.templateName(), template.versionNumber(), keyHash, requestHash, principal.userId(), now));

        Map<String, UUID> instanceIdByKey = new LinkedHashMap<>();
        List<OnboardingStepInstance> createdSteps = new ArrayList<>();
        Set<String> applicableKeys = applicable.stream().map(WorkflowSnapshotService.StepSnapshot::stepKey).collect(Collectors.toSet());
        for (var source : applicable) {
            List<String> applicableDependencies = source.dependencyKeys().stream().filter(applicableKeys::contains).toList();
            OnboardingStepStatus initial = initialStatus(source.dependencyMode(), applicableDependencies);
            UUID stepId = UUID.randomUUID();
            instanceIdByKey.put(source.stepKey(), stepId);
            Instant dueAt = source.dueAfterHours() == null ? null : now.plus(source.dueAfterHours(), ChronoUnit.HOURS);
            createdSteps.add(new OnboardingStepInstance(stepId, principal.organizationId(), instance.getId(), template.versionId(),
                    source.sourceStepId(), source.stepKey(), source.name(), source.description(), source.stepType(), source.displayOrder(),
                    source.required(), source.blocking(), source.clientVisible(), source.requiresReview(), source.dependencyMode(),
                    source.conditionExpression(), source.assignedRoleId(), dueAt, source.reminderPolicyId(), source.allowSkip(),
                    source.allowReopen(), source.configuration(), initial, principal.userId(), now));
        }
        steps.saveAllAndFlush(createdSteps);

        List<OnboardingStepInstanceDependency> dependencySnapshots = new ArrayList<>();
        for (var source : applicable) {
            UUID stepId = instanceIdByKey.get(source.stepKey());
            for (String dependencyKey : source.dependencyKeys()) {
                UUID dependencyId = instanceIdByKey.get(dependencyKey);
                if (dependencyId != null) {
                    dependencySnapshots.add(new OnboardingStepInstanceDependency(principal.organizationId(), instance.getId(), stepId, dependencyId, now));
                }
            }
        }
        if (!dependencySnapshots.isEmpty()) dependencies.saveAllAndFlush(dependencySnapshots);

        for (OnboardingStepInstance step : createdSteps) {
            var created = new OnboardingStepInitializer.StepCreated(principal.organizationId(), project.id(), project.clientId(),
                    instance.getId(), step.getId(), step.getStepType(), step.getName(), step.getDescription(), step.getStatus(),
                    step.isClientVisible(), step.isRequiresReview(), step.getAssignedRoleId(), step.getDueAt(), step.getReminderPolicyId(),
                    step.getConfigurationJson(), principal.userId(), now);
            stepInitializers.stream().filter(initializer -> initializer.supports(step.getStepType()))
                    .forEach(initializer -> initializer.initialize(created));
        }

        boolean ready = OnboardingReadinessPolicy.isReady(createdSteps.stream()
                .map(step -> new OnboardingReadinessPolicy.Requirement(step.isBlocking(), step.getStatus())).toList());
        instance.updateReadiness(ready, principal.userId(), now);
        onboardings.saveAndFlush(instance);

        activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(), "ONBOARDING_CREATED",
                "ONBOARDING", instance.getId(), "Onboarding created from workflow snapshot",
                Map.of("templateId", template.templateId(), "templateVersionId", template.versionId(), "templateVersion", template.versionNumber()));
        audit.record(principal.organizationId(), principal.userId(), "ONBOARDING_CREATED", "ONBOARDING", instance.getId(), null,
                Map.of("projectId", projectId, "templateId", template.templateId(), "templateVersionId", template.versionId(),
                        "templateVersion", template.versionNumber(), "applicableSteps", createdSteps.size(), "ready", ready), servletRequest);
        outbox.record(principal.organizationId(), "ONBOARDING_CREATED", "ONBOARDING", instance.getId(),
                Map.of("onboardingId", instance.getId(), "projectId", projectId, "templateVersionId", template.versionId()));
        return response(principal.organizationId(), instance);
    }

    @Transactional(readOnly = true)
    public OnboardingResponse get(TenantPrincipal principal, UUID onboardingId) {
        return response(principal.organizationId(), require(principal.organizationId(), onboardingId));
    }

    @Transactional(readOnly = true)
    public OnboardingResponse getByProject(TenantPrincipal principal, UUID projectId) {
        projectAccess.require(principal.organizationId(), projectId);
        OnboardingInstance instance = onboardings.findByOrganizationIdAndProjectId(principal.organizationId(), projectId)
                .orElseThrow(OnboardingService::notFound);
        return response(principal.organizationId(), instance);
    }

    @Transactional(readOnly = true)
    public List<OnboardingStepResponse> steps(TenantPrincipal principal, UUID onboardingId) {
        require(principal.organizationId(), onboardingId);
        return responseSteps(principal.organizationId(), onboardingId);
    }

    private OnboardingResponse requireSameRequest(UUID organizationId, OnboardingInstance instance, String requestHash) {
        if (!instance.getStartRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for a different onboarding request.");
        }
        return response(organizationId, instance);
    }

    private OnboardingResponse response(UUID organizationId, OnboardingInstance instance) {
        List<OnboardingStepResponse> stepResponses = responseSteps(organizationId, instance.getId());
        var progress = OnboardingProgressPolicy.calculate(stepResponses.stream()
                .map(step -> new OnboardingProgressPolicy.Requirement(step.stepType(), step.required(), step.blocking(), step.status())).toList());
        return new OnboardingResponse(instance.getId(), instance.getProjectId(), instance.getTemplateId(), instance.getTemplateVersionId(),
                instance.getTemplateNameSnapshot(), instance.getTemplateVersionNumber(), instance.getStatus(), instance.isReady(), progress.percentage(),
                progress.completed(), progress.total(), instance.getStartedAt(), instance.getCompletedAt(), instance.getUpdatedAt(), instance.getVersion(), stepResponses);
    }

    private List<OnboardingStepResponse> responseSteps(UUID organizationId, UUID onboardingId) {
        List<OnboardingStepInstance> values = steps.findAllByOrganizationIdAndOnboardingIdOrderByDisplayOrderAscIdAsc(organizationId, onboardingId);
        Map<UUID, String> keyById = values.stream().collect(Collectors.toMap(OnboardingStepInstance::getId, OnboardingStepInstance::getStepKey));
        Map<UUID, List<String>> dependencyKeys = dependencies.findAllByOrganizationIdAndOnboardingId(organizationId, onboardingId).stream()
                .collect(Collectors.groupingBy(OnboardingStepInstanceDependency::getStepInstanceId,
                        Collectors.mapping(edge -> keyById.get(edge.getDependsOnStepInstanceId()), Collectors.toList())));
        return values.stream().map(step -> new OnboardingStepResponse(step.getId(), step.getStepKey(), step.getName(), step.getDescription(),
                step.getStepType(), step.getDisplayOrder(), step.isRequired(), step.isBlocking(), step.isClientVisible(), step.isRequiresReview(),
                step.getDependencyMode(), dependencyKeys.getOrDefault(step.getId(), List.of()).stream().filter(java.util.Objects::nonNull).sorted().toList(),
                step.getConditionExpression(), step.getAssignedRoleId(), step.getDueAt(), step.getReminderPolicyId(), step.isAllowSkip(),
                step.isAllowReopen(), step.getConfigurationJson(), step.getStatus(), step.getCompletedAt(), step.getVersion())).toList();
    }

    private OnboardingInstance require(UUID organizationId, UUID onboardingId) {
        return onboardings.findByOrganizationIdAndId(organizationId, onboardingId).orElseThrow(OnboardingService::notFound);
    }

    private static OnboardingStepStatus initialStatus(DependencyMode mode, List<String> applicableDependencies) {
        if (mode == DependencyMode.NONE) return OnboardingStepStatus.AVAILABLE;
        if (mode == DependencyMode.ALL && applicableDependencies.isEmpty()) return OnboardingStepStatus.AVAILABLE;
        return OnboardingStepStatus.LOCKED;
    }

    private static void validateApplicableDependencies(List<WorkflowSnapshotService.StepSnapshot> applicable) {
        Set<String> allKeys = applicable.stream().map(WorkflowSnapshotService.StepSnapshot::stepKey).collect(Collectors.toSet());
        for (var step : applicable) {
            if (step.dependencyMode() == DependencyMode.ANY
                    && step.dependencyKeys().stream().noneMatch(allKeys::contains)) {
                throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_DEPENDENCY_UNSATISFIABLE",
                        "Applicable step '" + step.stepKey() + "' has no applicable dependency that can satisfy ANY mode.");
            }
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested onboarding instance was not found.");
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The project changed. Refresh and try again.");
    }
}
