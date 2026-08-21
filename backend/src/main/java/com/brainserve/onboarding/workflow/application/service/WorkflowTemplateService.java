package com.brainserve.onboarding.workflow.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.organization.application.service.OrganizationRoleAccessService;
import com.brainserve.onboarding.workflow.api.request.CreateWorkflowTemplateRequest;
import com.brainserve.onboarding.workflow.api.request.CreateWorkflowVersionRequest;
import com.brainserve.onboarding.workflow.api.request.SaveWorkflowDraftRequest;
import com.brainserve.onboarding.workflow.api.request.UpdateWorkflowTemplateRequest;
import com.brainserve.onboarding.workflow.api.request.WorkflowStepDraftRequest;
import com.brainserve.onboarding.workflow.api.response.WorkflowStepResponse;
import com.brainserve.onboarding.workflow.api.response.WorkflowTemplateDetailResponse;
import com.brainserve.onboarding.workflow.api.response.WorkflowTemplateSummaryResponse;
import com.brainserve.onboarding.workflow.api.response.WorkflowVersionDetailResponse;
import com.brainserve.onboarding.workflow.api.response.WorkflowVersionSummaryResponse;
import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepDependency;
import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplate;
import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateStep;
import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateVersion;
import com.brainserve.onboarding.workflow.domain.model.WorkflowVersionStatus;
import com.brainserve.onboarding.workflow.infrastructure.persistence.WorkflowStepDependencyRepository;
import com.brainserve.onboarding.workflow.infrastructure.persistence.WorkflowTemplateRepository;
import com.brainserve.onboarding.workflow.infrastructure.persistence.WorkflowTemplateStepRepository;
import com.brainserve.onboarding.workflow.infrastructure.persistence.WorkflowTemplateVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowTemplateService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONFIGURATION_BYTES = 32_000;

    private final WorkflowTemplateRepository templates;
    private final WorkflowTemplateVersionRepository versions;
    private final WorkflowTemplateStepRepository steps;
    private final WorkflowStepDependencyRepository dependencies;
    private final WorkflowConditionEngine conditions;
    private final OrganizationRoleAccessService roleAccess;
    private final List<WorkflowStepConfigurationValidator> configurationValidators;
    private final List<WorkflowReminderPolicyValidator> reminderPolicyValidators;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WorkflowTemplateService(WorkflowTemplateRepository templates,
                                   WorkflowTemplateVersionRepository versions,
                                   WorkflowTemplateStepRepository steps,
                                   WorkflowStepDependencyRepository dependencies,
                                   WorkflowConditionEngine conditions,
                                   OrganizationRoleAccessService roleAccess,
                                   List<WorkflowStepConfigurationValidator> configurationValidators,
                                   List<WorkflowReminderPolicyValidator> reminderPolicyValidators,
                                   AuditService audit,
                                   ObjectMapper objectMapper,
                                   Clock clock) {
        this.templates = templates;
        this.versions = versions;
        this.steps = steps;
        this.dependencies = dependencies;
        this.conditions = conditions;
        this.roleAccess = roleAccess;
        this.configurationValidators = List.copyOf(configurationValidators);
        this.reminderPolicyValidators = List.copyOf(reminderPolicyValidators);
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public WorkflowTemplateDetailResponse create(TenantPrincipal principal, CreateWorkflowTemplateRequest request,
                                                 HttpServletRequest servletRequest) {
        String name = normalizedName(request.name());
        if (templates.existsByOrganizationIdAndNameIgnoreCaseAndStatus(principal.organizationId(), name, WorkflowTemplateStatus.ACTIVE)) {
            throw duplicateName();
        }
        Instant now = clock.instant();
        WorkflowTemplate template = templates.saveAndFlush(new WorkflowTemplate(
                UUID.randomUUID(), principal.organizationId(), name, request.description(), principal.userId(), now));
        WorkflowTemplateVersion version = versions.saveAndFlush(new WorkflowTemplateVersion(
                UUID.randomUUID(), principal.organizationId(), template.getId(), 1, request.initialChangeNote(), principal.userId(), now));
        audit.record(principal.organizationId(), principal.userId(), "WORKFLOW_TEMPLATE_CREATED", "WORKFLOW_TEMPLATE", template.getId(), null,
                Map.of("name", template.getName(), "draftVersion", version.getVersionNumber()), servletRequest);
        return detail(template);
    }

    @Transactional(readOnly = true)
    public PageResult<WorkflowTemplateSummaryResponse> list(TenantPrincipal principal, int page, int size, boolean includeArchived) {
        var pageable = PageRequest.of(Math.max(page, 0), safeSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<WorkflowTemplate> result = includeArchived
                ? templates.findAllByOrganizationId(principal.organizationId(), pageable)
                : templates.findAllByOrganizationIdAndStatus(principal.organizationId(), WorkflowTemplateStatus.ACTIVE, pageable);
        Set<UUID> ids = result.getContent().stream().map(WorkflowTemplate::getId).collect(Collectors.toSet());
        Map<UUID, List<WorkflowTemplateVersion>> byTemplate = versions.findAllByOrganizationIdAndTemplateIdIn(principal.organizationId(), ids)
                .stream().collect(Collectors.groupingBy(WorkflowTemplateVersion::getTemplateId));
        List<WorkflowTemplateSummaryResponse> items = result.getContent().stream()
                .map(template -> summary(template, byTemplate.getOrDefault(template.getId(), List.of()))).toList();
        return new PageResult<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public WorkflowTemplateDetailResponse get(TenantPrincipal principal, UUID templateId) {
        return detail(requireTemplate(principal.organizationId(), templateId));
    }

    @Transactional
    public WorkflowTemplateDetailResponse update(TenantPrincipal principal, UUID templateId, UpdateWorkflowTemplateRequest request,
                                                 HttpServletRequest servletRequest) {
        WorkflowTemplate template = requireTemplate(principal.organizationId(), templateId);
        requireVersion(template.getVersion(), request.version());
        String name = normalizedName(request.name());
        if (templates.existsByOrganizationIdAndNameIgnoreCaseAndStatusAndIdNot(
                principal.organizationId(), name, WorkflowTemplateStatus.ACTIVE, templateId)) throw duplicateName();
        Map<String, Object> before = Map.of("name", template.getName(), "description", safe(template.getDescription()), "version", template.getVersion());
        try {
            template.update(name, request.description(), principal.userId(), clock.instant());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw invalidState(ex.getMessage());
        }
        templates.saveAndFlush(template);
        audit.record(principal.organizationId(), principal.userId(), "WORKFLOW_TEMPLATE_UPDATED", "WORKFLOW_TEMPLATE", templateId, before,
                Map.of("name", template.getName(), "description", safe(template.getDescription()), "version", template.getVersion()), servletRequest);
        return detail(template);
    }

    @Transactional
    public WorkflowTemplateDetailResponse archive(TenantPrincipal principal, UUID templateId, long expectedVersion,
                                                  HttpServletRequest servletRequest) {
        WorkflowTemplate template = requireTemplate(principal.organizationId(), templateId);
        requireVersion(template.getVersion(), expectedVersion);
        WorkflowTemplateStatus before = template.getStatus();
        template.archive(principal.userId(), clock.instant());
        templates.saveAndFlush(template);
        audit.record(principal.organizationId(), principal.userId(), "WORKFLOW_TEMPLATE_ARCHIVED", "WORKFLOW_TEMPLATE", templateId,
                Map.of("status", before), Map.of("status", template.getStatus()), servletRequest);
        return detail(template);
    }

    @Transactional
    public WorkflowVersionDetailResponse createVersion(TenantPrincipal principal, UUID templateId,
                                                       CreateWorkflowVersionRequest request, HttpServletRequest servletRequest) {
        WorkflowTemplate template = requireActiveTemplateForUpdate(principal.organizationId(), templateId);
        if (versions.existsByOrganizationIdAndTemplateIdAndStatus(principal.organizationId(), templateId, WorkflowVersionStatus.DRAFT)) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_DRAFT_EXISTS", "Publish or continue the existing draft before creating another version.");
        }
        WorkflowTemplateVersion source = versions.findFirstByOrganizationIdAndTemplateIdAndStatusOrderByVersionNumberDesc(
                principal.organizationId(), templateId, WorkflowVersionStatus.PUBLISHED).orElse(null);
        int number = source == null ? 1 : source.getVersionNumber() + 1;
        Instant now = clock.instant();
        WorkflowTemplateVersion target = versions.saveAndFlush(new WorkflowTemplateVersion(
                UUID.randomUUID(), principal.organizationId(), templateId, number, request.changeNote(), principal.userId(), now));
        if (source != null) cloneDefinition(principal, source.getId(), target.getId(), now);
        audit.record(principal.organizationId(), principal.userId(), "WORKFLOW_VERSION_CREATED", "WORKFLOW_TEMPLATE_VERSION", target.getId(), null,
                Map.of("templateId", template.getId(), "versionNumber", target.getVersionNumber(), "clonedFrom", source == null ? "" : source.getId().toString()), servletRequest);
        return versionDetail(principal.organizationId(), target);
    }

    @Transactional
    public WorkflowVersionDetailResponse saveDraft(TenantPrincipal principal, UUID versionId, SaveWorkflowDraftRequest request,
                                                   HttpServletRequest servletRequest) {
        WorkflowTemplateVersion version = versions.findForUpdate(principal.organizationId(), versionId).orElseThrow(WorkflowTemplateService::notFound);
        requireVersion(version.getVersion(), request.version());
        try { version.requireDraft(); } catch (IllegalStateException ex) { throw immutable(); }
        requireActiveTemplate(principal.organizationId(), version.getTemplateId());

        List<NormalizedStep> normalized = normalizeAndValidateSteps(principal.organizationId(), request.steps());
        roleAccess.requireActiveRoles(principal.organizationId(), normalized.stream().map(NormalizedStep::assignedRoleId).filter(java.util.Objects::nonNull).toList());
        validateGraph(normalized);

        dependencies.deleteAllByOrganizationIdAndTemplateVersionId(principal.organizationId(), versionId);
        dependencies.flush();
        steps.deleteAllByOrganizationIdAndTemplateVersionId(principal.organizationId(), versionId);
        steps.flush();

        Instant now = clock.instant();
        Map<String, WorkflowTemplateStep> inserted = new LinkedHashMap<>();
        for (NormalizedStep draft : normalized) {
            WorkflowTemplateStep entity = new WorkflowTemplateStep(UUID.randomUUID(), principal.organizationId(), versionId,
                    draft.stepKey(), draft.name(), draft.description(), draft.stepType(), draft.displayOrder(), draft.required(),
                    draft.blocking(), draft.clientVisible(), draft.requiresReview(), draft.dependencyMode(), draft.conditionExpression(),
                    draft.assignedRoleId(), draft.dueAfterHours(), draft.reminderPolicyId(), draft.allowSkip(), draft.allowReopen(),
                    draft.configuration(), principal.userId(), now);
            inserted.put(draft.stepKey(), entity);
        }
        steps.saveAll(inserted.values());
        steps.flush();
        List<WorkflowStepDependency> edges = new ArrayList<>();
        for (NormalizedStep draft : normalized) {
            WorkflowTemplateStep target = inserted.get(draft.stepKey());
            for (String dependencyKey : draft.dependencyKeys()) {
                edges.add(new WorkflowStepDependency(principal.organizationId(), versionId, target.getId(),
                        inserted.get(dependencyKey).getId(), principal.userId(), now));
            }
        }
        dependencies.saveAll(edges);
        dependencies.flush();
        version.revise(request.changeNote(), principal.userId(), now);
        versions.saveAndFlush(version);
        audit.record(principal.organizationId(), principal.userId(), "WORKFLOW_DRAFT_SAVED", "WORKFLOW_TEMPLATE_VERSION", versionId, null,
                Map.of("templateId", version.getTemplateId(), "versionNumber", version.getVersionNumber(), "stepCount", normalized.size()), servletRequest);
        return versionDetail(principal.organizationId(), version);
    }

    @Transactional
    public WorkflowVersionDetailResponse publish(TenantPrincipal principal, UUID versionId, long expectedVersion,
                                                 HttpServletRequest servletRequest) {
        WorkflowTemplateVersion version = versions.findForUpdate(principal.organizationId(), versionId).orElseThrow(WorkflowTemplateService::notFound);
        requireVersion(version.getVersion(), expectedVersion);
        try { version.requireDraft(); } catch (IllegalStateException ex) { throw immutable(); }
        requireActiveTemplate(principal.organizationId(), version.getTemplateId());
        List<WorkflowTemplateStep> currentSteps = steps.findAllByOrganizationIdAndTemplateVersionIdOrderByDisplayOrderAscIdAsc(principal.organizationId(), versionId);
        if (currentSteps.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_EMPTY", "A workflow version must contain at least one step before publishing.");
        }
        roleAccess.requireActiveRoles(principal.organizationId(), currentSteps.stream().map(WorkflowTemplateStep::getAssignedRoleId).filter(java.util.Objects::nonNull).toList());
        // Re-run persisted definition validation before making it immutable.
        validatePersistedGraph(principal.organizationId(), currentSteps, dependencies.findAllByOrganizationIdAndTemplateVersionId(principal.organizationId(), versionId));
        for (WorkflowTemplateStep step : currentSteps) {
            configurationValidators.stream().filter(validator -> validator.supports(step.getStepType()))
                    .forEach(validator -> validator.validate(principal.organizationId(), step.getStepKey(), step.getConfigurationJson()));
            if (step.getReminderPolicyId() != null) {
                if (reminderPolicyValidators.isEmpty()) {
                    throw new ApiException(HttpStatus.CONFLICT, "REMINDER_POLICY_NOT_AVAILABLE",
                            "Reminder policies are not available in this deployment.");
                }
                reminderPolicyValidators.forEach(validator -> validator.validate(principal.organizationId(), step.getReminderPolicyId()));
            }
        }
        version.publish(principal.userId(), clock.instant());
        versions.saveAndFlush(version);
        audit.record(principal.organizationId(), principal.userId(), "WORKFLOW_VERSION_PUBLISHED", "WORKFLOW_TEMPLATE_VERSION", versionId, null,
                Map.of("templateId", version.getTemplateId(), "versionNumber", version.getVersionNumber(), "stepCount", currentSteps.size()), servletRequest);
        return versionDetail(principal.organizationId(), version);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionDetailResponse getVersion(TenantPrincipal principal, UUID versionId) {
        WorkflowTemplateVersion version = versions.findByOrganizationIdAndId(principal.organizationId(), versionId).orElseThrow(WorkflowTemplateService::notFound);
        return versionDetail(principal.organizationId(), version);
    }

    private void cloneDefinition(TenantPrincipal principal, UUID sourceVersionId, UUID targetVersionId, Instant now) {
        List<WorkflowTemplateStep> sourceSteps = steps.findAllByOrganizationIdAndTemplateVersionIdOrderByDisplayOrderAscIdAsc(principal.organizationId(), sourceVersionId);
        List<WorkflowStepDependency> sourceEdges = dependencies.findAllByOrganizationIdAndTemplateVersionId(principal.organizationId(), sourceVersionId);
        Map<UUID, WorkflowTemplateStep> sourceById = sourceSteps.stream().collect(Collectors.toMap(WorkflowTemplateStep::getId, Function.identity()));
        Map<UUID, WorkflowTemplateStep> cloneBySource = new HashMap<>();
        for (WorkflowTemplateStep source : sourceSteps) {
            WorkflowTemplateStep clone = new WorkflowTemplateStep(UUID.randomUUID(), principal.organizationId(), targetVersionId,
                    source.getStepKey(), source.getName(), source.getDescription(), source.getStepType(), source.getDisplayOrder(),
                    source.isRequired(), source.isBlocking(), source.isClientVisible(), source.isRequiresReview(), source.getDependencyMode(),
                    source.getConditionExpression(), source.getAssignedRoleId(), source.getDueAfterHours(), source.getReminderPolicyId(),
                    source.isAllowSkip(), source.isAllowReopen(), source.getConfigurationJson(), principal.userId(), now);
            cloneBySource.put(source.getId(), clone);
        }
        steps.saveAll(cloneBySource.values());
        steps.flush();
        List<WorkflowStepDependency> clones = sourceEdges.stream().map(edge -> new WorkflowStepDependency(
                principal.organizationId(), targetVersionId, cloneBySource.get(edge.getStepId()).getId(),
                cloneBySource.get(edge.getDependsOnStepId()).getId(), principal.userId(), now)).toList();
        dependencies.saveAll(clones);
        dependencies.flush();
        if (sourceById.size() != cloneBySource.size()) throw new IllegalStateException("Workflow clone lost steps");
    }

    private List<NormalizedStep> normalizeAndValidateSteps(UUID organizationId, List<WorkflowStepDraftRequest> input) {
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        List<NormalizedStep> result = new ArrayList<>();
        for (WorkflowStepDraftRequest request : input) {
            String key;
            try { key = WorkflowTemplateStep.normalizeStepKey(request.stepKey()); }
            catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_STEP_INVALID", ex.getMessage()); }
            if (!keys.add(key)) throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_STEP_KEY_DUPLICATE", "Step keys must be unique within a version.");
            if (!orders.add(request.displayOrder())) throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_STEP_ORDER_DUPLICATE", "Display order values must be unique within a version.");
            JsonNode condition = conditions.normalizeAndValidate(request.conditionExpression());
            JsonNode configuration = normalizeConfiguration(request.configuration());
            if (request.stepType() == com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.PAYMENT && request.requiresReview()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_PAYMENT_CONFIGURATION_INVALID",
                        "PAYMENT steps cannot require manual review because verified provider events are authoritative.");
            }
            if (request.stepType() == com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.PLATFORM_ACCESS
                    && (!request.requiresReview() || !request.clientVisible())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_PLATFORM_ACCESS_INVALID",
                        "PLATFORM_ACCESS steps must be client-visible and require internal review because clients can submit access but cannot verify it.");
            }
            validateStepSemantics(request.stepType(), request.clientVisible(), request.requiresReview());
            if (request.reminderPolicyId() != null) {
                if (reminderPolicyValidators.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "REMINDER_POLICY_NOT_AVAILABLE",
                            "Reminder policies are not available in this deployment.");
                }
                reminderPolicyValidators.forEach(validator -> validator.validate(organizationId, request.reminderPolicyId()));
            }
            List<String> dependencyKeys;
            try {
                dependencyKeys = request.dependencyKeys().stream().map(WorkflowTemplateStep::normalizeStepKey).distinct().toList();
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_DEPENDENCY_INVALID", ex.getMessage());
            }
            result.add(new NormalizedStep(key, request.name(), request.description(), request.stepType(), request.displayOrder(),
                    request.required(), request.blocking(), request.clientVisible(), request.requiresReview(), request.dependencyMode(), condition,
                    request.assignedRoleId(), request.dueAfterHours(), request.reminderPolicyId(), request.allowSkip(), request.allowReopen(),
                    configuration, dependencyKeys));
        }
        return result.stream().sorted(java.util.Comparator.comparingInt(NormalizedStep::displayOrder)).toList();
    }

    private JsonNode normalizeConfiguration(JsonNode value) {
        JsonNode normalized = value == null || value.isNull() ? objectMapper.createObjectNode() : value;
        if (!normalized.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_CONFIGURATION_INVALID", "Step configuration must be a JSON object.");
        if (normalized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CONFIGURATION_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_CONFIGURATION_TOO_LARGE", "Step configuration is too large.");
        }
        return normalized.deepCopy();
    }

    private static void validateGraph(List<NormalizedStep> definitions) {
        Set<String> keys = definitions.stream().map(NormalizedStep::stepKey).collect(Collectors.toSet());
        Map<String, List<String>> graph = new HashMap<>();
        for (NormalizedStep step : definitions) {
            for (String dependency : step.dependencyKeys()) {
                if (!keys.contains(dependency)) throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_DEPENDENCY_INVALID", "Dependency '" + dependency + "' does not exist in this version.");
                if (dependency.equals(step.stepKey())) throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_DEPENDENCY_INVALID", "A step cannot depend on itself.");
            }
            if (step.dependencyMode() == DependencyMode.NONE && !step.dependencyKeys().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_DEPENDENCY_INVALID", "NONE dependency mode cannot contain dependencies.");
            }
            if (step.dependencyMode() != DependencyMode.NONE && step.dependencyKeys().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_DEPENDENCY_INVALID", step.dependencyMode() + " dependency mode requires at least one dependency.");
            }
            graph.put(step.stepKey(), step.dependencyKeys());
        }
        ensureAcyclic(graph);
    }

    private void validatePersistedGraph(UUID organizationId, List<WorkflowTemplateStep> persisted, List<WorkflowStepDependency> edges) {
        Map<UUID, String> keys = persisted.stream().collect(Collectors.toMap(WorkflowTemplateStep::getId, WorkflowTemplateStep::getStepKey));
        Map<String, List<String>> graph = new HashMap<>();
        for (WorkflowTemplateStep step : persisted) {
            conditions.normalizeAndValidate(step.getConditionExpression());
            if (step.getStepType() == com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.PAYMENT && step.isRequiresReview()) {
                throw invalidState("PAYMENT steps cannot require manual review because verified provider events are authoritative");
            }
            if (step.getStepType() == com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.PLATFORM_ACCESS
                    && (!step.isRequiresReview() || !step.isClientVisible())) {
                throw invalidState("PLATFORM_ACCESS steps must be client-visible and require internal review");
            }
            validatePersistedStepSemantics(step);
            List<String> deps = edges.stream().filter(edge -> edge.getStepId().equals(step.getId()))
                    .map(edge -> keys.get(edge.getDependsOnStepId())).toList();
            if (deps.stream().anyMatch(java.util.Objects::isNull)) throw invalidState("Workflow dependency references an unavailable step");
            if (step.getDependencyMode() == DependencyMode.NONE && !deps.isEmpty()) throw invalidState("NONE dependency mode cannot contain dependencies");
            if (step.getDependencyMode() != DependencyMode.NONE && deps.isEmpty()) throw invalidState(step.getDependencyMode() + " dependency mode requires dependencies");
            graph.put(step.getStepKey(), deps);
        }
        ensureAcyclic(graph);
    }

    private static void validateStepSemantics(com.brainserve.onboarding.workflow.domain.model.WorkflowStepType type,
                                              boolean clientVisible, boolean requiresReview) {
        if (type == com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.MANUAL_TASK && clientVisible) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_MANUAL_TASK_CLIENT_VISIBILITY_INVALID",
                    "Workflow-generated MANUAL_TASK steps are internal work. Assign separate client tasks explicitly when client ownership is required.");
        }
        if (type == com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.APPROVAL) {
            if (clientVisible) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_APPROVAL_CLIENT_VISIBILITY_INVALID",
                        "APPROVAL steps are internal decisions and cannot be client-visible.");
            }
            if (requiresReview) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_APPROVAL_REVIEW_INVALID",
                        "APPROVAL is already a review decision and cannot require a second workflow review flag.");
            }
        }
    }

    private static void validatePersistedStepSemantics(WorkflowTemplateStep step) {
        try {
            validateStepSemantics(step.getStepType(), step.isClientVisible(), step.isRequiresReview());
        } catch (ApiException ex) {
            throw invalidState(ex.getMessage());
        }
    }

    private static void ensureAcyclic(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String key : graph.keySet()) visit(key, graph, visited, visiting, new ArrayDeque<>());
    }

    private static void visit(String key, Map<String, List<String>> graph, Set<String> visited, Set<String> visiting, ArrayDeque<String> path) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) {
            path.addLast(key);
            throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_DEPENDENCY_CYCLE", "Workflow dependencies must be acyclic: " + String.join(" -> ", path));
        }
        path.addLast(key);
        for (String dependency : graph.getOrDefault(key, List.of())) visit(dependency, graph, visited, visiting, path);
        path.removeLast();
        visiting.remove(key);
        visited.add(key);
    }

    private WorkflowTemplateDetailResponse detail(WorkflowTemplate template) {
        List<WorkflowTemplateVersion> list = versions.findAllByOrganizationIdAndTemplateIdOrderByVersionNumberDesc(template.getOrganizationId(), template.getId());
        Map<UUID, Long> stepCounts = list.isEmpty() ? Map.of() : steps.countByVersionIds(
                        template.getOrganizationId(), list.stream().map(WorkflowTemplateVersion::getId).toList()).stream()
                .collect(Collectors.toMap(WorkflowTemplateStepRepository.StepCount::getVersionId,
                        WorkflowTemplateStepRepository.StepCount::getStepCount));
        List<WorkflowVersionSummaryResponse> versionResponses = list.stream().map(version -> new WorkflowVersionSummaryResponse(
                version.getId(), version.getVersionNumber(), version.getStatus(), version.getChangeNote(), version.getPublishedAt(),
                version.getUpdatedAt(), Math.toIntExact(stepCounts.getOrDefault(version.getId(), 0L)), version.getVersion())).toList();
        return new WorkflowTemplateDetailResponse(template.getId(), template.getName(), template.getDescription(), template.getStatus(),
                template.getArchivedAt(), template.getCreatedAt(), template.getUpdatedAt(), template.getVersion(), versionResponses);
    }

    private WorkflowVersionDetailResponse versionDetail(UUID organizationId, WorkflowTemplateVersion version) {
        List<WorkflowTemplateStep> currentSteps = steps.findAllByOrganizationIdAndTemplateVersionIdOrderByDisplayOrderAscIdAsc(organizationId, version.getId());
        List<WorkflowStepDependency> currentEdges = dependencies.findAllByOrganizationIdAndTemplateVersionId(organizationId, version.getId());
        Map<UUID, String> keys = currentSteps.stream().collect(Collectors.toMap(WorkflowTemplateStep::getId, WorkflowTemplateStep::getStepKey));
        Map<UUID, List<String>> byStep = currentEdges.stream().collect(Collectors.groupingBy(WorkflowStepDependency::getStepId,
                Collectors.mapping(edge -> keys.get(edge.getDependsOnStepId()), Collectors.toList())));
        List<WorkflowStepResponse> responses = currentSteps.stream().map(step -> new WorkflowStepResponse(
                step.getId(), step.getStepKey(), step.getName(), step.getDescription(), step.getStepType(), step.getDisplayOrder(),
                step.isRequired(), step.isBlocking(), step.isClientVisible(), step.isRequiresReview(), step.getDependencyMode(),
                step.getConditionExpression(), step.getAssignedRoleId(), step.getDueAfterHours(), step.getReminderPolicyId(),
                step.isAllowSkip(), step.isAllowReopen(), step.getConfigurationJson(),
                byStep.getOrDefault(step.getId(), List.of()).stream().sorted().toList())).toList();
        return new WorkflowVersionDetailResponse(version.getId(), version.getTemplateId(), version.getVersionNumber(), version.getStatus(),
                version.getChangeNote(), version.getPublishedAt(), version.getUpdatedAt(), version.getVersion(), responses);
    }

    private WorkflowTemplateSummaryResponse summary(WorkflowTemplate template, Collection<WorkflowTemplateVersion> list) {
        WorkflowTemplateVersion latestPublished = list.stream().filter(v -> v.getStatus() == WorkflowVersionStatus.PUBLISHED)
                .max(java.util.Comparator.comparingInt(WorkflowTemplateVersion::getVersionNumber)).orElse(null);
        Integer latest = latestPublished == null ? null : latestPublished.getVersionNumber();
        UUID latestId = latestPublished == null ? null : latestPublished.getId();
        Integer draft = list.stream().filter(v -> v.getStatus() == WorkflowVersionStatus.DRAFT)
                .map(WorkflowTemplateVersion::getVersionNumber).findFirst().orElse(null);
        return new WorkflowTemplateSummaryResponse(template.getId(), template.getName(), template.getDescription(), template.getStatus(), latest, latestId, draft,
                template.getUpdatedAt(), template.getVersion());
    }

    private WorkflowTemplate requireTemplate(UUID organizationId, UUID templateId) {
        return templates.findByOrganizationIdAndId(organizationId, templateId).orElseThrow(WorkflowTemplateService::notFound);
    }

    private WorkflowTemplate requireActiveTemplate(UUID organizationId, UUID templateId) {
        WorkflowTemplate template = requireTemplate(organizationId, templateId);
        if (template.getStatus() != WorkflowTemplateStatus.ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_TEMPLATE_ARCHIVED", "Archived templates cannot be modified or started.");
        return template;
    }

    private WorkflowTemplate requireActiveTemplateForUpdate(UUID organizationId, UUID templateId) {
        WorkflowTemplate template = templates.findForUpdate(organizationId, templateId).orElseThrow(WorkflowTemplateService::notFound);
        if (template.getStatus() != WorkflowTemplateStatus.ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_TEMPLATE_ARCHIVED", "Archived templates cannot be modified or started.");
        return template;
    }

    private static String normalizedName(String value) { return value == null ? null : value.trim(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static int safeSize(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }
    private static void requireVersion(long actual, long expected) { if (actual != expected) throw conflict(); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource was not found."); }
    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The resource changed. Refresh and try again."); }
    private static ApiException duplicateName() { return new ApiException(HttpStatus.CONFLICT, "WORKFLOW_TEMPLATE_NAME_EXISTS", "An active workflow template with this name already exists."); }
    private static ApiException immutable() { return new ApiException(HttpStatus.CONFLICT, "WORKFLOW_VERSION_IMMUTABLE", "Published workflow versions are immutable. Create a new version to make changes."); }
    private static ApiException invalidState(String message) { return new ApiException(HttpStatus.CONFLICT, "WORKFLOW_STATE_INVALID", message); }

    private record NormalizedStep(String stepKey, String name, String description,
                                  com.brainserve.onboarding.workflow.domain.model.WorkflowStepType stepType,
                                  int displayOrder, boolean required, boolean blocking, boolean clientVisible, boolean requiresReview,
                                  DependencyMode dependencyMode, JsonNode conditionExpression, UUID assignedRoleId, Integer dueAfterHours,
                                  UUID reminderPolicyId, boolean allowSkip, boolean allowReopen, JsonNode configuration,
                                  List<String> dependencyKeys) {}

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
