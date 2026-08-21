package com.brainserve.onboarding.payments.api.response;
import com.brainserve.onboarding.payments.domain.model.PaymentTransactionType;import java.time.Instant;import java.util.UUID;
public record PaymentTransactionResponse(UUID id,UUID paymentId,PaymentTransactionType type,long amountMinor,String currency,String reason,Instant occurredAt) {}
