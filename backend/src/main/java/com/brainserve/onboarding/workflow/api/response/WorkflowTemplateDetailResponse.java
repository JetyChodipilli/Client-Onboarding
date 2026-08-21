package com.brainserve.onboarding.workflow.api.response;

import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowTemplateDetailResponse(
        UUID id, String name, String description, WorkflowTemplateStatus status, Instant archivedAt,
        Instant createdAt, Instant updatedAt, long version, List<WorkflowVersionSummaryResponse> versions) {}
