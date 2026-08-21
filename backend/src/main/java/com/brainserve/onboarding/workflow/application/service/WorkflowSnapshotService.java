package com.brainserve.onboarding.workflow.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepDependency;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only application boundary used by onboarding to create an immutable instance snapshot. */
@Service
public class WorkflowSnapshotService {
    private final WorkflowTemplateRepository templates;
    private final WorkflowTemplateVersionRepository versions;
    private final WorkflowTemplateStepRepository steps;
    private final WorkflowStepDependencyRepository dependencies;

    public WorkflowSnapshotService(WorkflowTemplateRepository templates, WorkflowTemplateVersionRepository versions,
                                   WorkflowTemplateStepRepository steps, WorkflowStepDependencyRepository dependencies) {
        this.templates = templates;
        this.versions = versions;
        this.steps = steps;
        this.dependencies = dependencies;
    }

    @Transactional(readOnly = true)
    public TemplateSnapshot requirePublished(UUID organizationId, UUID versionId) {
        WorkflowTemplateVersion version = versions.findByOrganizationIdAndIdAndStatus(organizationId, versionId, WorkflowVersionStatus.PUBLISHED)
                .orElseThrow(WorkflowSnapshotService::notFound);
        WorkflowTemplate template = templates.findByOrganizationIdAndId(organizationId, version.getTemplateId())
                .orElseThrow(WorkflowSnapshotService::notFound);
        if (template.getStatus() != WorkflowTemplateStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_TEMPLATE_ARCHIVED", "Archived workflow templates cannot start new onboardings.");
        }
        List<WorkflowTemplateStep> sourceSteps = steps.findAllByOrganizationIdAndTemplateVersionIdOrderByDisplayOrderAscIdAsc(organizationId, versionId);
        if (sourceSteps.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_EMPTY", "Published workflow version contains no steps.");
        Map<UUID, String> keyById = sourceSteps.stream().collect(Collectors.toMap(WorkflowTemplateStep::getId, WorkflowTemplateStep::getStepKey));
        Map<UUID, List<String>> dependencyKeys = dependencies.findAllByOrganizationIdAndTemplateVersionId(organizationId, versionId).stream()
                .collect(Collectors.groupingBy(WorkflowStepDependency::getStepId,
                        Collectors.mapping(edge -> keyById.get(edge.getDependsOnStepId()), Collectors.toList())));
        List<StepSnapshot> snapshots = sourceSteps.stream().map(step -> new StepSnapshot(
                step.getId(), step.getStepKey(), step.getName(), step.getDescription(), step.getStepType(), step.getDisplayOrder(),
                step.isRequired(), step.isBlocking(), step.isClientVisible(), step.isRequiresReview(), step.getDependencyMode(),
                step.getConditionExpression(), step.getAssignedRoleId(), step.getDueAfterHours(), step.getReminderPolicyId(),
                step.isAllowSkip(), step.isAllowReopen(), step.getConfigurationJson(),
                dependencyKeys.getOrDefault(step.getId(), List.of()).stream().sorted().toList())).toList();
        return new TemplateSnapshot(template.getId(), template.getName(), version.getId(), version.getVersionNumber(), snapshots);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested workflow version was not found.");
    }

    public record TemplateSnapshot(UUID templateId, String templateName, UUID versionId, int versionNumber, List<StepSnapshot> steps) {
        public TemplateSnapshot { steps = List.copyOf(steps); }
    }

    public record StepSnapshot(UUID sourceStepId, String stepKey, String name, String description, WorkflowStepType stepType,
                               int displayOrder, boolean required, boolean blocking, boolean clientVisible, boolean requiresReview,
                               DependencyMode dependencyMode, JsonNode conditionExpression, UUID assignedRoleId, Integer dueAfterHours,
                               UUID reminderPolicyId, boolean allowSkip, boolean allowReopen, JsonNode configuration,
                               List<String> dependencyKeys) {
        public StepSnapshot { dependencyKeys = List.copyOf(dependencyKeys); }
    }
}
