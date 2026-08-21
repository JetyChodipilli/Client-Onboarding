package com.brainserve.onboarding.payments.api.response;
import com.brainserve.onboarding.payments.domain.model.RefundStatus;import java.time.Instant;import java.util.UUID;
public record RefundResponse(UUID id,UUID paymentId,String provider,String providerRefundId,long amountMinor,String currency,RefundStatus status,String reason,String failureReason,Instant requestedAt,Instant completedAt,long version) {}
