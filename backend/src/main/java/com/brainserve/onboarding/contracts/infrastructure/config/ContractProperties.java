package com.brainserve.onboarding.contracts.infrastructure.config;
import java.time.Duration;import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("app.contracts")
public record ContractProperties(String provider,String webhookSecretBase64,String signingBaseUrl,Duration webhookMaxAge,int maxWebhookBytes,long maxSignedDocumentBytes){
 public ContractProperties{provider=blank(provider)?"DISABLED":provider.trim().toUpperCase(java.util.Locale.ROOT);signingBaseUrl=blank(signingBaseUrl)?"http://localhost:3000/portal/contracts/sandbox":signingBaseUrl.trim();webhookMaxAge=webhookMaxAge==null?Duration.ofMinutes(5):webhookMaxAge;maxWebhookBytes=maxWebhookBytes<=0?262_144:Math.min(maxWebhookBytes,1_048_576);maxSignedDocumentBytes=maxSignedDocumentBytes<=0?20L*1024*1024:Math.min(maxSignedDocumentBytes,100L*1024*1024);}
 private static boolean blank(String v){return v==null||v.isBlank();}
}
