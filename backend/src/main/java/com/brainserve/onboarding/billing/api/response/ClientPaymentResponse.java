package com.brainserve.onboarding.billing.api.response;
import com.brainserve.onboarding.payments.domain.model.PaymentStatus;import java.time.Instant;import java.util.UUID;
public record ClientPaymentResponse(UUID id,long requestedAmountMinor,long capturedAmountMinor,long refundedAmountMinor,String currency,PaymentStatus status,Instant createdAt) {}
