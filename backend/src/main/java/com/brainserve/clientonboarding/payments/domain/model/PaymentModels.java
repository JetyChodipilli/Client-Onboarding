package com.brainserve.clientonboarding.payments.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class PaymentModels {
    private PaymentModels(){}
    public enum SessionStatus { CREATING, READY, UNKNOWN, SETTLED, FAILED }
    public enum TransactionStatus { INITIATED, PENDING, AUTHORIZED, CAPTURED, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED;
        public boolean captured(){return this==CAPTURED || this==REFUNDED || this==PARTIALLY_REFUNDED;}
    }
    public enum RefundStatus { CREATING, PENDING, UNKNOWN, PROCESSED, FAILED }
    public record Session(UUID id,UUID organizationId,UUID invoiceId,String providerOrderId,long amountMinor,String currency,SessionStatus status,String idempotencyKey,String requestHash,Instant createdAt,long version) {public boolean open(){return status==SessionStatus.CREATING || status==SessionStatus.READY || status==SessionStatus.UNKNOWN;}}
    public record Transaction(UUID id,UUID organizationId,UUID invoiceId,UUID paymentId,String provider,String providerPaymentId,TransactionStatus status,long amountMinor,long refundedMinor,String currency,String reference,String reason,Instant createdAt,long version){}
    public record Refund(UUID id,UUID organizationId,UUID transactionId,String providerRefundId,long amountMinor,RefundStatus status,String reason,String idempotencyKey,String requestHash,Instant createdAt,long version){}
    public record Balances(long captured,long refunded,long reserved){}
}
