package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.forms.domain.model.FormStatus;
import com.brainserve.onboarding.forms.domain.model.FormVersionStatus;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormTemplateRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormTemplateVersionRepository;
import com.brainserve.onboarding.workflow.application.service.WorkflowStepConfigurationValidator;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FormWorkflowConfigurationValidator implements WorkflowStepConfigurationValidator {
    private final FormTemplateVersionRepository versions;
    private final FormTemplateRepository forms;

    public FormWorkflowConfigurationValidator(FormTemplateVersionRepository versions, FormTemplateRepository forms) {
        this.versions = versions;
        this.forms = forms;
    }

    @Override public boolean supports(WorkflowStepType stepType) { return stepType == WorkflowStepType.FORM; }

    @Override
    public void validate(UUID organizationId, String stepKey, JsonNode configuration) {
        JsonNode raw = configuration == null ? null : configuration.get("formVersionId");
        UUID versionId;
        try { versionId = raw != null && raw.isTextual() ? UUID.fromString(raw.asText()) : null; }
        catch (IllegalArgumentException ex) { versionId = null; }
        if (versionId == null) throw invalid(stepKey, "FORM steps require a published formVersionId.");
        var version = versions.findByOrganizationIdAndId(organizationId, versionId).orElseThrow(() -> invalid(stepKey, "Configured form version was not found."));
        if (version.getStatus() != FormVersionStatus.PUBLISHED) throw invalid(stepKey, "Configured form version must be published.");
        var form = forms.findByOrganizationIdAndId(organizationId, version.getFormId()).orElseThrow(() -> invalid(stepKey, "Configured form was not found."));
        if (form.getStatus() != FormStatus.ACTIVE) throw invalid(stepKey, "Archived forms cannot be configured on new workflow versions.");
    }

    private static ApiException invalid(String stepKey, String message) {
        return new ApiException(HttpStatus.CONFLICT, "WORKFLOW_FORM_CONFIGURATION_INVALID", "Step " + stepKey + ": " + message);
    }
}
