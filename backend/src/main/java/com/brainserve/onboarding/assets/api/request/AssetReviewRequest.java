package com.brainserve.onboarding.assets.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssetReviewRequest(@PositiveOrZero long version, @NotBlank @Size(max = 2000) String note) {}
