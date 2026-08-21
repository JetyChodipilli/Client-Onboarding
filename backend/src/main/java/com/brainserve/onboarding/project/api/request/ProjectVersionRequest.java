package com.brainserve.onboarding.project.api.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ProjectVersionRequest(@PositiveOrZero long version) {}
