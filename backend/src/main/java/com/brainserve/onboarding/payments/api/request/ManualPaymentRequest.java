package com.brainserve.onboarding.payments.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ManualPaymentRequest(@Positive long amountMinor, @NotBlank @Size(max = 2000) String reason) {}
