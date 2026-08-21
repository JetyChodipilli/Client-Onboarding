package com.brainserve.onboarding.onboarding.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record StartOnboardingRequest(
        @NotNull UUID templateVersionId,
        @PositiveOrZero long projectVersion) {}
