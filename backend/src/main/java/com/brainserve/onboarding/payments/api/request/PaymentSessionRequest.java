package com.brainserve.onboarding.payments.api.request;

import jakarta.validation.constraints.Positive;

public record PaymentSessionRequest(@Positive Long amountMinor) {}
