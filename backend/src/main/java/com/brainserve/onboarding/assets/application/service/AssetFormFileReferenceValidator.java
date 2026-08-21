package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.assets.domain.model.Asset;
import com.brainserve.onboarding.assets.domain.model.AssetRequirement;
import com.brainserve.onboarding.assets.domain.model.AssetVersion;
import com.brainserve.onboarding.assets.domain.model.AssetVersionStatus;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRequirementRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetVersionRepository;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.forms.application.service.FormFileReferenceValidator;
import com.brainserve.onboarding.forms.domain.model.FormField;
import com.brainserve.onboarding.forms.domain.model.FormFieldType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validates that FILE answers point only to clean, current, same-field/same-project Asset versions. */
@Service
public class AssetFormFileReferenceValidator implements FormFileReferenceValidator {
    private final AssetVersionRepository versions;
    private final AssetRepository assets;
    private final AssetRequirementRepository requirements;

    public AssetFormFileReferenceValidator(AssetVersionRepository versions, AssetRepository assets,
                                           AssetRequirementRepository requirements) {
        this.versions = versions;
        this.assets = assets;
        this.requirements = requirements;
    }

    @Override
    public void validate(UUID organizationId, UUID projectId, UUID stepId, UUID submissionId,
                         List<FormField> fields, Map<String, JsonNode> answers) {
        for (FormField field : fields) {
            if (field.getFieldType() != FormFieldType.FILE) continue;
            JsonNode answer = answers.get(field.getFieldKey());
            if (answer == null || answer.isNull() || !answer.isTextual() || answer.asText().isBlank()) continue;
            UUID versionId;
            try { versionId = UUID.fromString(answer.asText().trim()); }
            catch (IllegalArgumentException ex) { throw invalid(field.getFieldKey()); }

            AssetVersion version = versions.findByOrganizationIdAndId(organizationId, versionId).orElseThrow(() -> invalid(field.getFieldKey()));
            if (version.getStatus() != AssetVersionStatus.CLEAN || !version.getProjectId().equals(projectId)) throw invalid(field.getFieldKey());
            Asset asset = assets.findByOrganizationIdAndId(organizationId, version.getAssetId()).orElseThrow(() -> invalid(field.getFieldKey()));
            if (!versionId.equals(asset.getCurrentVersionId()) || !asset.getProjectId().equals(projectId) || !asset.getStepInstanceId().equals(stepId)) {
                throw invalid(field.getFieldKey());
            }
            AssetRequirement requirement = requirements.findByOrganizationIdAndId(organizationId, asset.getRequirementId())
                    .orElseThrow(() -> invalid(field.getFieldKey()));
            // Reuse from an earlier response version in the same questionnaire is allowed; cross-field/step/project reuse is not.
            if (!projectId.equals(requirement.getProjectId()) || !stepId.equals(requirement.getStepInstanceId())
                    || !field.getId().equals(requirement.getFormFieldId()) || requirement.getFormSubmissionId() == null) {
                throw invalid(field.getFieldKey());
            }
        }
    }

    private static ApiException invalid(String fieldKey) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FORM_FILE_REFERENCE_INVALID",
                fieldKey + ": Select a clean file uploaded for this questionnaire field.");
    }
}
