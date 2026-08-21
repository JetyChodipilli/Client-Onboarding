package com.brainserve.onboarding.workflow.api.response;

import com.brainserve.onboarding.workflow.domain.model.WorkflowVersionStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkflowVersionSummaryResponse(
        UUID id, int versionNumber, WorkflowVersionStatus status, String changeNote,
        Instant publishedAt, Instant updatedAt, int stepCount, long version) {}
