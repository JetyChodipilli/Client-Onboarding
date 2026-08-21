package com.brainserve.onboarding.workflow.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveWorkflowDraftRequest(
        @Min(0) long version,
        @Size(max = 500) String changeNote,
        @NotNull @Size(max = 100) List<@Valid WorkflowStepDraftRequest> steps) {}
