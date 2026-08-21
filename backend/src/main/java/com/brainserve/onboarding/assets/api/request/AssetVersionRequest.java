package com.brainserve.onboarding.assets.api.request;

import jakarta.validation.constraints.PositiveOrZero;

public record AssetVersionRequest(@PositiveOrZero long version) {}
