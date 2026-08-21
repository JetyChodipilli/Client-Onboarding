package com.brainserve.onboarding.payments.infrastructure.provider;

import java.time.Instant;import java.util.Map;import java.util.UUID;

public interface PaymentGateway {
    String providerCode();
    CheckoutSession createCheckout(CheckoutCommand command);
    RefundRequest requestRefund(RefundCommand command);
    VerifiedWebhook verifyWebhook(byte[] body, Map<String,String> headers, Instant now);
    boolean available();

    record CheckoutCommand(UUID organizationId,UUID projectId,UUID invoiceId,UUID paymentId,String idempotencyKey,long amountMinor,String currency,Instant expiresAt){}
    record CheckoutSession(String providerPaymentId,String providerSessionId,String checkoutUrl,Instant expiresAt){}
    record RefundCommand(UUID organizationId,UUID invoiceId,UUID paymentId,UUID refundId,String providerPaymentId,String idempotencyKey,long amountMinor,String currency,String reason){}
    record RefundRequest(String providerRefundId){}
    record VerifiedWebhook(String eventId,String eventType,UUID organizationId,UUID invoiceId,UUID paymentId,String providerPaymentId,
                           String providerTransactionId,String providerRefundId,long amountMinor,String currency,Instant occurredAt,String reason){}
}
