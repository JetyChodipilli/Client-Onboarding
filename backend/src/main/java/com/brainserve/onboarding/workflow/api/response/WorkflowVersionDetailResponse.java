package com.brainserve.onboarding.workflow.api.response;

import com.brainserve.onboarding.workflow.domain.model.WorkflowVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowVersionDetailResponse(
        UUID id, UUID templateId, int versionNumber, WorkflowVersionStatus status, String changeNote,
        Instant publishedAt, Instant updatedAt, long version, List<WorkflowStepResponse> steps) {}
