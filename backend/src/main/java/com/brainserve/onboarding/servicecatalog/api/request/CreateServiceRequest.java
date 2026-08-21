package com.brainserve.onboarding.servicecatalog.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateServiceRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description) {}
