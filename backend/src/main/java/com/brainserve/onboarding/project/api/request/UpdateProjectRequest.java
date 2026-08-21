package com.brainserve.onboarding.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateProjectRequest(
        @NotNull UUID clientId,
        @NotNull UUID serviceId,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 2000) String description,
        @PositiveOrZero long version) {}
