package com.brainserve.onboarding.payments.api.response;
import java.time.Instant;import java.util.UUID;
public record PaymentSessionResponse(UUID paymentId,String provider,String checkoutUrl,Instant expiresAt,long amountMinor,String currency) {}
