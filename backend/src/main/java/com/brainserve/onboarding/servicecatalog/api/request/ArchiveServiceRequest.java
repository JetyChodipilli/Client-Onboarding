package com.brainserve.onboarding.servicecatalog.api.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ArchiveServiceRequest(@PositiveOrZero long version) {}
