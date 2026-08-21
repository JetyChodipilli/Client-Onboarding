package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.forms.domain.model.FormField;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application port used by Forms to validate opaque secure-file references without reading Asset tables directly.
 * The Asset module supplies the implementation in Phase 6.
 */
public interface FormFileReferenceValidator {
    void validate(UUID organizationId, UUID projectId, UUID stepId, UUID submissionId,
                  List<FormField> fields, Map<String, JsonNode> answers);
}
