package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.workflow.application.service.WorkflowStepConfigurationValidator;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetWorkflowConfigurationValidator implements WorkflowStepConfigurationValidator {
    private final AssetPolicy policy;
    public AssetWorkflowConfigurationValidator(AssetPolicy policy) { this.policy = policy; }
    @Override public boolean supports(WorkflowStepType stepType) { return stepType == WorkflowStepType.FILE_UPLOAD; }
    @Override public void validate(UUID organizationId, String stepKey, JsonNode configuration) { policy.fromConfiguration(configuration); }
}
