package com.brainserve.clientonboarding.payments.application;

import java.util.*;

public interface PaymentProvider {
    boolean available(UUID organizationId);
    String publicKey(UUID organizationId);
    Order createOrder(UUID organizationId,UUID receipt,long amountMinor);
    Order order(UUID organizationId,String orderId);
    List<Payment> orderPayments(UUID organizationId,String orderId);
    Payment payment(UUID organizationId,String paymentId);
    Refund createRefund(UUID organizationId,String paymentId,UUID receipt,long amountMinor);
    Refund refund(UUID organizationId,String refundId);
    boolean verifyWebhook(UUID organizationId,byte[] body,String signature);
    boolean verifyCheckout(UUID organizationId,String orderId,String paymentId,String signature);
    record Order(String id,String receipt,long amountMinor,String currency,String status){}
    record Payment(String id,String orderId,long amountMinor,long refundedMinor,String currency,String status,boolean captured){}
    record Refund(String id,String paymentId,String receipt,long amountMinor,String currency,String status){}
}
