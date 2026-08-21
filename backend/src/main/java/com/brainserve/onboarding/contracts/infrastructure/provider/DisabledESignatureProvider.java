package com.brainserve.onboarding.contracts.infrastructure.provider;
import java.time.Instant;import java.util.Map;import org.springframework.stereotype.Component;
@Component
public class DisabledESignatureProvider implements ESignatureProvider{
 public String providerCode(){return "DISABLED";}public boolean available(){return false;}public SendResult send(SendCommand c){throw new IllegalStateException("E-signature provider is disabled");}public void voidDocument(String id,String reason){throw new IllegalStateException("E-signature provider is disabled");}public VerifiedCallback verifyWebhook(byte[] b,Map<String,String> h,Instant n){throw new IllegalStateException("E-signature provider is disabled");}public SignedDocument downloadSignedDocument(String id){throw new IllegalStateException("E-signature provider is disabled");}
}
