package com.brainserve.onboarding.workflow.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkflowTemplateRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        @Min(0) long version) {}
