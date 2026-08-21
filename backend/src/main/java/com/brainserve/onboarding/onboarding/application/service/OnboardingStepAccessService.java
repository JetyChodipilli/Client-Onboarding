package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only application boundary exposed to feature-owned workflow step handlers. */
@Service
public class OnboardingStepAccessService {
    private final OnboardingInstanceRepository onboardings;
    private final OnboardingStepInstanceRepository steps;

    public OnboardingStepAccessService(OnboardingInstanceRepository onboardings, OnboardingStepInstanceRepository steps) {
        this.onboardings = onboardings;
        this.steps = steps;
    }

    @Transactional(readOnly = true)
    public StepRef require(UUID organizationId, UUID stepId) {
        OnboardingStepInstance step = steps.findByOrganizationIdAndId(organizationId, stepId)
                .orElseThrow(OnboardingStepAccessService::notFound);
        OnboardingInstance onboarding = onboardings.findByOrganizationIdAndId(organizationId, step.getOnboardingId())
                .orElseThrow(OnboardingStepAccessService::notFound);
        return map(onboarding, step);
    }

    @Transactional(readOnly = true)
    public StepRef requireForProject(UUID organizationId, UUID projectId, UUID stepId) {
        StepRef step = require(organizationId, stepId);
        if (!step.projectId().equals(projectId)) throw notFound();
        return step;
    }

    @Transactional
    public StepRef requireForProjectForUpdate(UUID organizationId, UUID projectId, UUID stepId) {
        OnboardingStepInstance step = steps.findForUpdate(organizationId, stepId)
                .orElseThrow(OnboardingStepAccessService::notFound);
        OnboardingInstance onboarding = onboardings.findByOrganizationIdAndId(organizationId, step.getOnboardingId())
                .orElseThrow(OnboardingStepAccessService::notFound);
        StepRef result = map(onboarding, step);
        if (!result.projectId().equals(projectId)) throw notFound();
        return result;
    }

    private static StepRef map(OnboardingInstance onboarding, OnboardingStepInstance step) {
        return new StepRef(step.getId(), step.getOnboardingId(), onboarding.getProjectId(), step.getStepKey(),
                step.getName(), step.getDescription(), step.getStepType(), step.getStatus(), step.isRequired(),
                step.isBlocking(), step.isClientVisible(), step.isRequiresReview(), step.isAllowSkip(), step.isAllowReopen(), step.getConfigurationJson(), step.getVersion());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested onboarding step was not found.");
    }

    public record StepRef(UUID id, UUID onboardingId, UUID projectId, String stepKey, String name, String description,
                          WorkflowStepType stepType, OnboardingStepStatus status, boolean required, boolean blocking,
                          boolean clientVisible, boolean requiresReview, boolean allowSkip, boolean allowReopen, JsonNode configuration, long version) {}
}
