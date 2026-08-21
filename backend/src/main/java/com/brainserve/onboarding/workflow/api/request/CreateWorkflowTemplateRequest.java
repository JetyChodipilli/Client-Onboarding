package com.brainserve.onboarding.workflow.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkflowTemplateRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) String initialChangeNote) {}
