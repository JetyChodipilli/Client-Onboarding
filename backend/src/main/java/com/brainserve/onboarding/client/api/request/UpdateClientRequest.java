package com.brainserve.onboarding.client.api.request;

import com.brainserve.onboarding.client.domain.model.ClientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @NotBlank @Size(max = 180) String name,
        @NotNull ClientStatus status,
        @PositiveOrZero long version) {}
