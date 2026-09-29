package com.brainserve.clientonboarding.onboarding.application;

import java.util.EnumSet;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.ReadinessPolicy;
import com.brainserve.clientonboarding.onboarding.domain.model.StepStatePolicy;
import com.brainserve.clientonboarding.onboarding.domain.repository.OnboardingRepository;
import com.brainserve.clientonboarding.project.application.ProjectWorkflowPort;
import com.brainserve.clientonboarding.project.domain.model.ProjectRecord;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateVersion;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowCondition;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowGraphPolicy;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowTemplate;
import com.brainserve.clientonboarding.workflow.domain.repository.WorkflowTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private static final String START_SCOPE = "START_ONBOARDING";
    private final OnboardingRepository onboardings;
    private final WorkflowTemplateRepository workflows;
    private final ProjectWorkflowPort projects;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;
    private final List<com.brainserve.clientonboarding.workflow.application.StepConfigurationValidator> validators;

    public OnboardingService(OnboardingRepository onboardings, WorkflowTemplateRepository workflows,
                             ProjectWorkflowPort projects, AuditService audit, ObjectMapper json, Clock clock,
                             List<com.brainserve.clientonboarding.workflow.application.StepConfigurationValidator> validators) {
        this.onboardings = onboardings; this.workflows = workflows; this.projects = projects;
        this.audit = audit; this.json = json; this.clock = clock;
        this.validators = validators;
    }

    @PreAuthorize("hasAuthority('ONBOARDING_START')")
    @Transactional
    public OnboardingView start(TenantPrincipal principal, UUID projectId, StartCommand command,
                                String idempotencyKey, RequestMetadata metadata) {
        String key = validateIdempotencyKey(idempotencyKey);
        String fingerprint = hash(projectId + "|" + command.templateVersionId() + "|" + command.projectVersion());
        if (key != null) {
            // A tenant row lock makes the check-and-record sequence safe across application instances.
            onboardings.lockTenantCommands(principal.organizationId());
            var prior = onboardings.findIdempotentCommand(principal.organizationId(), START_SCOPE, key);
            if (prior.isPresent()) {
                if (!prior.get().requestFingerprint().equals(fingerprint)) {
                    throw new DomainException("IDEMPOTENCY_KEY_REUSED",
                            "This idempotency key was already used for a different request.", HttpStatus.CONFLICT);
                }
                return getInternal(principal.organizationId(), prior.get().resourceId());
            }
        }
        if (onboardings.findByProject(principal.organizationId(), projectId).isPresent()) {
            throw new DomainException("ONBOARDING_ALREADY_EXISTS",
                    "This project already has an onboarding instance.", HttpStatus.CONFLICT);
        }
        ProjectRecord project = projects.requireProject(principal.organizationId(), projectId);
        TemplateVersion version = workflows.findVersion(principal.organizationId(), command.templateVersionId())
                .filter(value -> value.status() == TemplateVersion.Status.PUBLISHED).orElseThrow(this::notFound);
        WorkflowTemplate template = workflows.findTemplate(principal.organizationId(), version.templateId())
                .filter(value -> value.status() == WorkflowTemplate.Status.ACTIVE).orElseThrow(this::notFound);
        if (!workflows.lockActiveTemplate(principal.organizationId(), template.id())) throw notFound();
        if (template.serviceId() != null && !template.serviceId().equals(project.serviceId())) {
            throw new DomainException("WORKFLOW_SERVICE_MISMATCH",
                    "The workflow template does not apply to this project's service.", HttpStatus.CONFLICT);
        }
        List<TemplateStep> sourceSteps = workflows.findSteps(principal.organizationId(), version.id());
        sourceSteps.forEach(step -> validators.forEach(validator -> validator.validate(principal.organizationId(), step)));
        try { WorkflowGraphPolicy.validate(sourceSteps); }
        catch (IllegalArgumentException exception) {
            throw new DomainException("INVALID_WORKFLOW_GRAPH", exception.getMessage(), HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        UUID onboardingId = UUID.randomUUID();
        List<OnboardingStepInstance> steps = snapshotSteps(principal.organizationId(), onboardingId,
                project, sourceSteps, now);
        boolean ready = ReadinessPolicy.ready(steps);
        String snapshot = snapshotJson(template, version, sourceSteps);
        OnboardingInstance instance = new OnboardingInstance(onboardingId, principal.organizationId(), projectId,
                template.id(), version.id(), version.versionNumber(), snapshot, OnboardingInstance.Status.DRAFT,
                ready, now, null, now, now, 0);
        onboardings.insert(instance, steps, principal.userId());
        projects.beginOnboarding(principal.organizationId(), projectId, command.projectVersion(), principal.userId(), now);
        if (key != null) onboardings.insertIdempotency(principal.organizationId(), START_SCOPE, key, instance.id(),
                fingerprint, now);
        audit.append(principal.organizationId(), principal.userId(), "ONBOARDING_CREATED", "ONBOARDING",
                instance.id(), Map.of(), Map.of("projectId", projectId, "templateVersionId", version.id(),
                        "stepCount", steps.size()), "API", metadata.ipHash());
        return new OnboardingView(instance, steps, ReadinessPolicy.progress(steps));
    }

    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public OnboardingView get(TenantPrincipal principal, UUID onboardingId) {
        return getInternal(principal.organizationId(), onboardingId);
    }

    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public OnboardingView getByProject(TenantPrincipal principal, UUID projectId) {
        OnboardingInstance value = onboardings.findByProject(principal.organizationId(), projectId)
                .orElseThrow(this::notFound);
        return getInternal(principal.organizationId(), value.id());
    }

    @PreAuthorize("hasAnyAuthority('ONBOARDING_START','ONBOARDING_REVIEW')")
    @Transactional
    public OnboardingView transitionStep(TenantPrincipal principal, UUID stepId, StepCommand command,
                                         RequestMetadata metadata) {
        OnboardingStepInstance step = onboardings.findStep(principal.organizationId(), stepId)
                .orElseThrow(this::notFound);
        if (step.stepType() == TemplateStep.StepType.FORM || step.stepType() == TemplateStep.StepType.FILE_UPLOAD) throw new DomainException("DEDICATED_STEP_FLOW_REQUIRED",
                "Use the dedicated submission and review actions for this step.", HttpStatus.CONFLICT);
        boolean reviewAction = requiresReviewPermission(step, command.targetStatus());
        if (reviewAction && !principal.hasPermission("ONBOARDING_REVIEW")) {
            throw new DomainException("PERMISSION_DENIED",
                    "Onboarding review permission is required for this transition.", HttpStatus.FORBIDDEN);
        }
        if (!reviewAction && !principal.hasPermission("ONBOARDING_START")) {
            throw new DomainException("PERMISSION_DENIED",
                    "Onboarding start permission is required for this transition.", HttpStatus.FORBIDDEN);
        }
        if (!StepStatePolicy.permits(step, command.targetStatus())) {
            throw new DomainException("INVALID_STEP_TRANSITION",
                    "The requested onboarding step transition is not allowed.", HttpStatus.CONFLICT);
        }
        OnboardingInstance onboarding = onboardings.findById(principal.organizationId(), step.onboardingId())
                .orElseThrow(this::notFound);
        projects.lockOnboardingProject(principal.organizationId(), onboarding.projectId());
        if (EnumSet.of(OnboardingInstance.Status.PAUSED, OnboardingInstance.Status.EXPIRED,
                OnboardingInstance.Status.CANCELLED, OnboardingInstance.Status.APPROVED,
                OnboardingInstance.Status.COMPLETED).contains(onboarding.status())) {
            throw new DomainException("ONBOARDING_NOT_ACTIVE",
                    "This onboarding is not accepting updates.", HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        if (!onboardings.updateStepStatus(principal.organizationId(), stepId, step.status(), command.targetStatus(),
                command.version(), principal.userId(), now)) throw conflict();
        onboardings.refreshAvailability(principal.organizationId(), step.onboardingId(), principal.userId(), now);
        List<OnboardingStepInstance> updatedSteps = onboardings.findSteps(principal.organizationId(),
                step.onboardingId());
        boolean ready = ReadinessPolicy.ready(updatedSteps);
        if (!onboardings.updateReadiness(principal.organizationId(), onboarding.id(), ready, onboarding.version(),
                principal.userId(), now)) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "ONBOARDING_STEP_TRANSITIONED",
                "ONBOARDING_STEP", stepId, Map.of("status", step.status()),
                Map.of("status", command.targetStatus(), "ready", ready), "API", metadata.ipHash());
        return getInternal(principal.organizationId(), onboarding.id());
    }

    private OnboardingView getInternal(UUID organizationId, UUID onboardingId) {
        OnboardingInstance instance = onboardings.findById(organizationId, onboardingId).orElseThrow(this::notFound);
        List<OnboardingStepInstance> steps = onboardings.findSteps(organizationId, onboardingId);
        return new OnboardingView(instance, steps, ReadinessPolicy.progress(steps));
    }

    private List<OnboardingStepInstance> snapshotSteps(UUID organizationId, UUID onboardingId,
                                                       ProjectRecord project, List<TemplateStep> source,
                                                       Instant now) {
        Map<WorkflowCondition.Field, String> context = new HashMap<>();
        context.put(WorkflowCondition.Field.SERVICE_CODE, project.serviceCode());
        context.put(WorkflowCondition.Field.CLIENT_STATUS, project.clientStatus());
        if (project.valueMinor() != null) context.put(WorkflowCondition.Field.PROJECT_VALUE_MINOR,
                project.valueMinor().toString());
        if (project.currencyCode() != null) context.put(WorkflowCondition.Field.CURRENCY_CODE, project.currencyCode());
        Map<UUID, Boolean> applicable = new LinkedHashMap<>();
        source.forEach(step -> applicable.put(step.id(), step.condition() == null || step.condition().evaluate(context)));
        boolean changed;
        do {
            changed = false;
            for (TemplateStep step : source) {
                if (Boolean.TRUE.equals(applicable.get(step.id()))
                        && step.dependencyMode() == TemplateStep.DependencyMode.ANY
                        && !step.dependencyStepIds().isEmpty()
                        && step.dependencyStepIds().stream().noneMatch(id -> Boolean.TRUE.equals(applicable.get(id)))) {
                    applicable.put(step.id(), false);
                    changed = true;
                }
            }
        } while (changed);
        Map<UUID, UUID> instanceIds = new HashMap<>();
        source.forEach(step -> instanceIds.put(step.id(), UUID.randomUUID()));
        return source.stream().map(step -> {
            boolean enabled = Boolean.TRUE.equals(applicable.get(step.id()));
            List<UUID> dependencies = step.dependencyStepIds().stream()
                    .filter(id -> Boolean.TRUE.equals(applicable.get(id))).map(instanceIds::get).toList();
            boolean immediatelyAvailable = enabled && (step.dependencyMode() == TemplateStep.DependencyMode.NONE
                    || step.dependencyMode() == TemplateStep.DependencyMode.ALL && dependencies.isEmpty());
            Instant dueAt = enabled && step.dueAfterHours() != null
                    ? now.plusSeconds(step.dueAfterHours().longValue() * 3600) : null;
            return new OnboardingStepInstance(instanceIds.get(step.id()), organizationId, onboardingId, step.id(),
                    step.stepKey(), step.name(), step.description(), step.stepType(), step.displayOrder(),
                    step.required(), step.blocking(), step.clientVisible(), step.requiresReview(),
                    step.dependencyMode(), step.condition(), step.assignedRole(), dueAt, step.allowSkip(),
                    step.allowReopen(), step.configuration(), enabled,
                    immediatelyAvailable ? OnboardingStepInstance.Status.AVAILABLE
                            : OnboardingStepInstance.Status.LOCKED,
                    dependencies, null, now, now, 0);
        }).toList();
    }

    private String snapshotJson(WorkflowTemplate template, TemplateVersion version, List<TemplateStep> steps) {
        try {
            return json.writeValueAsString(Map.of("templateId", template.id(), "templateName", template.name(),
                    "templateVersionId", version.id(), "versionNumber", version.versionNumber(), "steps", steps));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Workflow snapshot serialization failed", exception);
        }
    }

    private boolean requiresReviewPermission(OnboardingStepInstance step,
                                             OnboardingStepInstance.Status status) {
        return status == OnboardingStepInstance.Status.UNDER_REVIEW
                || status == OnboardingStepInstance.Status.NEEDS_REVISION
                || step.requiresReview() && status == OnboardingStepInstance.Status.COMPLETED;
    }

    private String validateIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() < 8 || clean.length() > 120 || !clean.matches("[A-Za-z0-9._:-]+")) {
            throw new DomainException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 120 safe characters.", HttpStatus.BAD_REQUEST);
        }
        return clean;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "The resource changed. Refresh and try again.", HttpStatus.CONFLICT); }

    public record StartCommand(UUID templateVersionId, long projectVersion) { }
    public record StepCommand(OnboardingStepInstance.Status targetStatus, long version) { }
    public record OnboardingView(OnboardingInstance onboarding, List<OnboardingStepInstance> steps, int progress) { }
}
