package com.brainserve.onboarding.client.api.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ArchiveClientRequest(@PositiveOrZero long version) {}
