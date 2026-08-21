package com.brainserve.onboarding.workflow.api.response;

import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkflowTemplateSummaryResponse(
        UUID id, String name, String description, WorkflowTemplateStatus status,
        Integer latestPublishedVersion, UUID latestPublishedVersionId, Integer draftVersion, Instant updatedAt, long version) {}
