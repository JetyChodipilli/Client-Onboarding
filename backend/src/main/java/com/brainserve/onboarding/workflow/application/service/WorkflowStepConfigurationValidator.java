package com.brainserve.onboarding.workflow.application.service;

import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Extension point allowing a feature module to validate configuration for the workflow step type it owns. */
public interface WorkflowStepConfigurationValidator {
    boolean supports(WorkflowStepType stepType);
    void validate(UUID organizationId, String stepKey, JsonNode configuration);
}
