package com.brainserve.onboarding.servicecatalog.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateServiceRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @PositiveOrZero long version) {}
