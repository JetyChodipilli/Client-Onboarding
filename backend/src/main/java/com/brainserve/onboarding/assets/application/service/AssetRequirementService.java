package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.assets.domain.model.AssetRequirement;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRequirementRepository;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetRequirementService {
    public static final String WORKFLOW_REQUIREMENT_KEY = "PRIMARY_FILE";

    private final AssetRequirementRepository requirements;
    private final OnboardingStepAccessService stepAccess;
    private final AssetPolicy policy;
    private final Clock clock;

    public AssetRequirementService(AssetRequirementRepository requirements, OnboardingStepAccessService stepAccess,
                                   AssetPolicy policy, Clock clock) {
        this.requirements = requirements;
        this.stepAccess = stepAccess;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public Context ensureWorkflowRequirement(UUID organizationId, UUID projectId, UUID stepId, UUID actorId) {
        OnboardingStepAccessService.StepRef step = stepAccess.requireForProjectForUpdate(organizationId, projectId, stepId);
        if (step.stepType() != WorkflowStepType.FILE_UPLOAD) throw notFound();
        AssetPolicy.Policy configured = policy.fromConfiguration(step.configuration());
        AssetRequirement existing = requirements.findByOrganizationIdAndStepInstanceIdAndRequirementKey(
                organizationId, stepId, WORKFLOW_REQUIREMENT_KEY).orElse(null);
        if (existing != null) return new Context(existing, step, policy(existing));

        Instant now = clock.instant();
        AssetRequirement created = requirements.saveAndFlush(new AssetRequirement(
                UUID.randomUUID(), organizationId, projectId, step.onboardingId(), step.id(), null, null,
                WORKFLOW_REQUIREMENT_KEY, step.name(), step.description(), configured.allowedMimeTypesJson(),
                configured.maxFileSizeBytes(), step.required(), step.requiresReview(), actorId, now));
        return new Context(created, step, configured);
    }

    @Transactional(readOnly = true)
    public Context requireWorkflowRequirement(UUID organizationId, UUID projectId, UUID stepId) {
        OnboardingStepAccessService.StepRef step = stepAccess.requireForProject(organizationId, projectId, stepId);
        if (step.stepType() != WorkflowStepType.FILE_UPLOAD) throw notFound();
        AssetPolicy.Policy configured = policy.fromConfiguration(step.configuration());
        AssetRequirement requirement = requirements.findByOrganizationIdAndStepInstanceIdAndRequirementKey(
                organizationId, stepId, WORKFLOW_REQUIREMENT_KEY).orElse(null);
        return new Context(requirement, step, requirement == null ? configured : policy(requirement));
    }

    public AssetPolicy.Policy policy(AssetRequirement requirement) {
        return new AssetPolicy.Policy(requirement.getMaxFileSizeBytes(),
                AssetRequirementJson.mimeSet(requirement.getAllowedMimeTypes()), requirement.getAllowedMimeTypes());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested asset requirement was not found.");
    }

    public record Context(AssetRequirement requirement, OnboardingStepAccessService.StepRef step, AssetPolicy.Policy policy) {}
}
