package com.brainserve.onboarding.contracts.infrastructure.provider;
import java.time.Instant;import java.util.*;
public interface ESignatureProvider{
 String providerCode();boolean available();SendResult send(SendCommand command);void voidDocument(String providerDocumentId,String reason);VerifiedCallback verifyWebhook(byte[] body,Map<String,String> headers,Instant now);SignedDocument downloadSignedDocument(String providerDocumentId);
 record Recipient(String id,String name,String email,int signingOrder){}
 record SendCommand(UUID organizationId,UUID projectId,UUID contractId,UUID contractVersionId,String idempotencyKey,String title,String legalContent,List<Recipient> recipients,Instant expiresAt){public SendCommand{recipients=List.copyOf(recipients);}}
 record SendResult(String providerDocumentId,String signingUrl,Map<String,String> recipientIds){public SendResult{recipientIds=Map.copyOf(recipientIds);}}
 record VerifiedCallback(String eventId,String eventType,UUID organizationId,UUID contractId,UUID contractVersionId,String providerDocumentId,String providerSignatureId,String recipientEmail,String signatoryName,Instant occurredAt){}
 record SignedDocument(byte[] bytes,String contentType){public SignedDocument{bytes=bytes.clone();}}
}
