package com.brainserve.clientonboarding.workflow.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.servicecatalog.domain.repository.ServiceCatalogRepository;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateVersion;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowCondition;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowGraphPolicy;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowTemplate;
import com.brainserve.clientonboarding.workflow.domain.repository.WorkflowTemplateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowTemplateService {
    private final WorkflowTemplateRepository workflows;
    private final ServiceCatalogRepository services;
    private final AuditService audit;
    private final Clock clock;

    public WorkflowTemplateService(WorkflowTemplateRepository workflows, ServiceCatalogRepository services,
                                   AuditService audit, Clock clock) {
        this.workflows = workflows; this.services = services; this.audit = audit; this.clock = clock;
    }

    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public PageSlice<WorkflowTemplate> list(TenantPrincipal principal, String search, int page, int size) {
        return workflows.findPage(principal.organizationId(), search == null ? "" : search.trim(),
                validPage(page), validSize(size));
    }

    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public WorkflowTemplate getTemplate(TenantPrincipal principal, UUID templateId) {
        return workflows.findTemplate(principal.organizationId(), templateId).orElseThrow(this::notFound);
    }

    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    @Transactional
    public TemplateBundle create(TenantPrincipal principal, TemplateCommand command, RequestMetadata metadata) {
        validateService(principal.organizationId(), command.serviceId());
        Instant now = clock.instant();
        WorkflowTemplate template = new WorkflowTemplate(UUID.randomUUID(), principal.organizationId(),
                command.serviceId(), required(command.name(), 180), optional(command.description(), 1000),
                WorkflowTemplate.Status.ACTIVE, null, now, now, 0);
        workflows.insertTemplate(template, principal.userId());
        TemplateVersion version = new TemplateVersion(UUID.randomUUID(), principal.organizationId(), template.id(),
                1, TemplateVersion.Status.DRAFT, null, null, now, now, 0);
        workflows.insertVersion(version, principal.userId());
        audit.append(principal.organizationId(), principal.userId(), "WORKFLOW_TEMPLATE_CREATED",
                "WORKFLOW_TEMPLATE", template.id(), Map.of(), Map.of("name", template.name()),
                "API", metadata.ipHash());
        return new TemplateBundle(template, version, List.of());
    }

    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public List<TemplateVersion> versions(TenantPrincipal principal, UUID templateId) {
        getTemplate(principal, templateId);
        return workflows.findVersions(principal.organizationId(), templateId);
    }

    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public VersionBundle getVersion(TenantPrincipal principal, UUID versionId) {
        TemplateVersion version = workflows.findVersion(principal.organizationId(), versionId)
                .orElseThrow(this::notFound);
        return new VersionBundle(version, workflows.findSteps(principal.organizationId(), versionId));
    }

    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    @Transactional
    public VersionBundle createVersion(TenantPrincipal principal, UUID templateId, UUID sourceVersionId,
                                       RequestMetadata metadata) {
        WorkflowTemplate template = getTemplateForMutation(principal, templateId);
        Instant now = clock.instant();
        int nextVersion = workflows.nextVersionNumber(principal.organizationId(), template.id());
        if (nextVersion == 0) throw notFound();
        TemplateVersion version = new TemplateVersion(UUID.randomUUID(), principal.organizationId(), template.id(),
                nextVersion,
                TemplateVersion.Status.DRAFT, null, null, now, now, 0);
        workflows.insertVersion(version, principal.userId());
        List<TemplateStep> copied = sourceVersionId == null ? List.of()
                : copySteps(principal.organizationId(), templateId, sourceVersionId, version.id(), now);
        if (!copied.isEmpty() && !workflows.replaceDraftSteps(principal.organizationId(), version.id(), 0, copied,
                principal.userId(), now)) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "WORKFLOW_VERSION_CREATED",
                "WORKFLOW_TEMPLATE_VERSION", version.id(), Map.of(), Map.of("templateId", templateId,
                        "versionNumber", version.versionNumber()), "API", metadata.ipHash());
        return new VersionBundle(workflows.findVersion(principal.organizationId(), version.id()).orElseThrow(), copied);
    }

    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    @Transactional
    public VersionBundle replaceSteps(TenantPrincipal principal, UUID versionId, long expectedVersion,
                                      List<StepCommand> commands,
                                      RequestMetadata metadata) {
        TemplateVersion version = requireDraft(principal.organizationId(), versionId);
        Instant now = clock.instant();
        List<TemplateStep> steps = sanitizeSteps(principal.organizationId(), versionId, commands, now);
        validateGraph(steps);
        if (!workflows.replaceDraftSteps(principal.organizationId(), versionId, expectedVersion, steps,
                principal.userId(), now)) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "WORKFLOW_STEPS_REPLACED",
                "WORKFLOW_TEMPLATE_VERSION", versionId, Map.of(), Map.of("stepCount", steps.size()),
                "API", metadata.ipHash());
        return new VersionBundle(workflows.findVersion(principal.organizationId(), versionId).orElseThrow(),
                workflows.findSteps(principal.organizationId(), versionId));
    }

    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    @Transactional
    public VersionBundle publish(TenantPrincipal principal, UUID versionId, long versionNumber,
                                 RequestMetadata metadata) {
        TemplateVersion version = requireDraft(principal.organizationId(), versionId);
        List<TemplateStep> steps = workflows.findSteps(principal.organizationId(), versionId);
        validateGraph(steps);
        if (!workflows.publishVersion(principal.organizationId(), versionId, versionNumber, principal.userId(),
                clock.instant())) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "WORKFLOW_VERSION_PUBLISHED",
                "WORKFLOW_TEMPLATE_VERSION", versionId, Map.of("status", version.status()),
                Map.of("status", "PUBLISHED", "stepCount", steps.size()), "API", metadata.ipHash());
        return new VersionBundle(workflows.findVersion(principal.organizationId(), versionId).orElseThrow(), steps);
    }

    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    @Transactional
    public void archive(TenantPrincipal principal, UUID templateId, long version, RequestMetadata metadata) {
        WorkflowTemplate current = getTemplateForMutation(principal, templateId);
        if (!workflows.archiveTemplate(principal.organizationId(), templateId, version, principal.userId(),
                clock.instant())) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "WORKFLOW_TEMPLATE_ARCHIVED",
                "WORKFLOW_TEMPLATE", templateId, Map.of("status", current.status()), Map.of("status", "ARCHIVED"),
                "API", metadata.ipHash());
    }

    private List<TemplateStep> copySteps(UUID organizationId, UUID templateId, UUID sourceVersionId,
                                         UUID targetVersionId, Instant now) {
        TemplateVersion source = workflows.findVersion(organizationId, sourceVersionId).orElseThrow(this::notFound);
        if (!source.templateId().equals(templateId) || source.status() != TemplateVersion.Status.PUBLISHED) {
            throw new DomainException("WORKFLOW_SOURCE_NOT_PUBLISHED",
                    "Only a published version of this template can be copied.", HttpStatus.CONFLICT);
        }
        List<TemplateStep> old = workflows.findSteps(organizationId, sourceVersionId);
        Map<UUID, UUID> ids = new HashMap<>();
        old.forEach(step -> ids.put(step.id(), UUID.randomUUID()));
        return old.stream().map(step -> new TemplateStep(ids.get(step.id()), organizationId, targetVersionId,
                step.stepKey(), step.name(), step.description(), step.stepType(), step.displayOrder(),
                step.required(), step.blocking(), step.clientVisible(), step.requiresReview(), step.dependencyMode(),
                step.condition(), step.assignedRole(), step.dueAfterHours(), step.reminderPolicyId(),
                step.allowSkip(), step.allowReopen(), step.configuration(),
                step.dependencyStepIds().stream().map(ids::get).toList(), now, now, 0)).toList();
    }

    private List<TemplateStep> sanitizeSteps(UUID organizationId, UUID versionId, List<StepCommand> commands,
                                             Instant now) {
        if (commands == null || commands.isEmpty() || commands.size() > 200) {
            throw validation("A workflow version must contain 1 to 200 steps.");
        }
        List<TemplateStep> result = new ArrayList<>();
        for (StepCommand command : commands) {
            UUID id = command.id() == null ? UUID.randomUUID() : command.id();
            String key = required(command.stepKey(), 80).toUpperCase(Locale.ROOT);
            if (!key.matches("[A-Z0-9][A-Z0-9_-]*")) throw validation("Step keys use A-Z, 0-9, underscore, or hyphen.");
            if (command.stepType() == null || command.dependencyMode() == null || command.displayOrder() < 0
                    || command.dueAfterHours() != null && (command.dueAfterHours() < 0
                    || command.dueAfterHours() > 87600)) throw validation("Step configuration is invalid.");
            try {
                if (command.condition() != null) new WorkflowCondition(command.condition().field(),
                        command.condition().operator(), command.condition().value());
            } catch (IllegalArgumentException exception) { throw validation(exception.getMessage()); }
            result.add(new TemplateStep(id, organizationId, versionId, key, required(command.name(), 180),
                    optional(command.description(), 1000), command.stepType(), command.displayOrder(),
                    command.required(), command.blocking(), command.clientVisible(), command.requiresReview(),
                    command.dependencyMode(), command.condition(), optional(command.assignedRole(), 100),
                    command.dueAfterHours(), command.reminderPolicyId(), command.allowSkip(), command.allowReopen(),
                    command.configuration() == null ? Map.of() : command.configuration(),
                    command.dependencyStepIds() == null ? List.of() : command.dependencyStepIds(), now, now, 0));
        }
        return List.copyOf(result);
    }

    private void validateGraph(List<TemplateStep> steps) {
        try { WorkflowGraphPolicy.validate(steps); }
        catch (IllegalArgumentException exception) { throw validation(exception.getMessage()); }
    }

    private TemplateVersion requireDraft(UUID organizationId, UUID versionId) {
        TemplateVersion version = workflows.findVersion(organizationId, versionId).orElseThrow(this::notFound);
        if (!workflows.lockActiveTemplate(organizationId, version.templateId())) throw notFound();
        if (version.status() != TemplateVersion.Status.DRAFT) {
            throw new DomainException("WORKFLOW_VERSION_IMMUTABLE",
                    "Published workflow versions cannot be edited.", HttpStatus.CONFLICT);
        }
        return version;
    }

    private WorkflowTemplate getTemplateForMutation(TenantPrincipal principal, UUID id) {
        WorkflowTemplate template = getTemplate(principal, id);
        if (template.status() == WorkflowTemplate.Status.ARCHIVED) throw notFound();
        return template;
    }

    private void validateService(UUID organizationId, UUID serviceId) {
        if (serviceId == null) return;
        var service = services.findById(organizationId, serviceId).orElseThrow(this::notFound);
        if (service.archivedAt() != null) throw notFound();
    }

    private String required(String value, int max) { String clean = value == null ? "" : value.trim(); if (clean.isEmpty() || clean.length() > max) throw validation("A required workflow value is invalid."); return clean; }
    private String optional(String value, int max) { if (value == null || value.isBlank()) return null; String clean = value.trim(); if (clean.length() > max) throw validation("A workflow value is too long."); return clean; }
    private int validPage(int value) { if (value < 0) throw validation("Page must be non-negative."); return value; }
    private int validSize(int value) { if (value < 1 || value > 100) throw validation("Size must be 1 to 100."); return value; }
    private DomainException validation(String message) { return new DomainException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST); }
    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "The resource changed. Refresh and try again.", HttpStatus.CONFLICT); }

    public record TemplateCommand(String name, String description, UUID serviceId) { }
    public record StepCommand(UUID id, String stepKey, String name, String description,
                              TemplateStep.StepType stepType, int displayOrder, boolean required, boolean blocking,
                              boolean clientVisible, boolean requiresReview,
                              TemplateStep.DependencyMode dependencyMode, WorkflowCondition condition,
                              String assignedRole, Integer dueAfterHours, UUID reminderPolicyId,
                              boolean allowSkip, boolean allowReopen, Map<String, Object> configuration,
                              List<UUID> dependencyStepIds) { }
    public record TemplateBundle(WorkflowTemplate template, TemplateVersion draftVersion,
                                 List<TemplateStep> steps) { }
    public record VersionBundle(TemplateVersion version, List<TemplateStep> steps) { }
}
