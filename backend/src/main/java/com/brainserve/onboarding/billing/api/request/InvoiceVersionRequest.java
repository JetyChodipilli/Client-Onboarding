package com.brainserve.onboarding.billing.api.request;

import jakarta.validation.constraints.PositiveOrZero;

public record InvoiceVersionRequest(@PositiveOrZero long version) {}
