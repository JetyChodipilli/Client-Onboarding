package com.brainserve.onboarding.workflow.api.request;

import jakarta.validation.constraints.Min;

public record WorkflowVersionRequest(@Min(0) long version) {}
