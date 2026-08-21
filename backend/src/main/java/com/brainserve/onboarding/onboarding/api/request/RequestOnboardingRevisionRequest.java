package com.brainserve.onboarding.onboarding.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RequestOnboardingRevisionRequest(
        @Min(0) long version,
        @NotBlank @Size(max = 2000) String reason,
        @NotEmpty @Size(max = 25) List<UUID> stepIds) {
    public RequestOnboardingRevisionRequest {
        stepIds = stepIds == null ? List.of() : List.copyOf(stepIds);
    }
}
