package com.brainserve.onboarding.payments.api.response;
import com.brainserve.onboarding.payments.domain.model.PaymentStatus;import java.time.Instant;import java.util.UUID;
public record PaymentResponse(UUID id,String provider,String providerPaymentId,long requestedAmountMinor,long capturedAmountMinor,long refundedAmountMinor,String currency,PaymentStatus status,String checkoutUrl,Instant sessionExpiresAt,String failureReason,Instant createdAt,long version) {}
