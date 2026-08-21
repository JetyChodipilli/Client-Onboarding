package com.brainserve.onboarding.payments.infrastructure.provider;
import java.time.Instant;import java.util.Map;import org.springframework.stereotype.Component;
@Component
public class DisabledPaymentGateway implements PaymentGateway{
 public String providerCode(){return "DISABLED";} public boolean available(){return false;}
 private static IllegalStateException off(){return new IllegalStateException("A payment provider is not configured");}
 public CheckoutSession createCheckout(CheckoutCommand c){throw off();} public RefundRequest requestRefund(RefundCommand c){throw off();}
 public VerifiedWebhook verifyWebhook(byte[] b,Map<String,String> h,Instant n){throw off();}
}
