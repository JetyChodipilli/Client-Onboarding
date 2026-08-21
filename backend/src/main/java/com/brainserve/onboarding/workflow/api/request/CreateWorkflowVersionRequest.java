package com.brainserve.onboarding.workflow.api.request;

import jakarta.validation.constraints.Size;

public record CreateWorkflowVersionRequest(@Size(max = 500) String changeNote) {}
