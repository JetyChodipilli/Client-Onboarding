package com.brainserve.onboarding.contracts.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.contracts.domain.model.*;
import com.brainserve.onboarding.contracts.infrastructure.config.ContractProperties;
import com.brainserve.onboarding.contracts.infrastructure.persistence.*;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProvider;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProviderRegistry;
import com.brainserve.onboarding.contracts.infrastructure.storage.ContractDocumentStorage;
import com.brainserve.onboarding.integrations.application.service.WebhookEventLedgerService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Verified, idempotent e-signature callback processor. */
@Service
public class ContractWebhookService {
    private final ESignatureProviderRegistry providers;
    private final ContractProperties properties;
    private final ContractRepository contracts;
    private final ContractVersionRepository versions;
    private final ContractRecipientRepository recipients;
    private final ContractSignatureRepository signatures;
    private final ContractDocumentStorage storage;
    private final WebhookEventLedgerService webhookLedger;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ContractWebhookService(ESignatureProviderRegistry providers, ContractProperties properties,
                                  ContractRepository contracts, ContractVersionRepository versions,
                                  ContractRecipientRepository recipients, ContractSignatureRepository signatures,
                                  ContractDocumentStorage storage, WebhookEventLedgerService webhookLedger,
                                  ActivityTimelineService activity, AuditService audit, OutboxService outbox,
                                  ApplicationEventPublisher events, Clock clock, TransactionTemplate tx) {
        this.providers=providers;this.properties=properties;this.contracts=contracts;this.versions=versions;
        this.recipients=recipients;this.signatures=signatures;this.storage=storage;this.webhookLedger=webhookLedger;
        this.activity=activity;this.audit=audit;this.outbox=outbox;this.events=events;this.clock=clock;this.tx=tx;
    }

    public WebhookResult process(String providerCode, byte[] body, Map<String,String> headers) {
        if (body==null || body.length==0 || body.length>properties.maxWebhookBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,"WEBHOOK_PAYLOAD_INVALID","Contract webhook payload size is invalid.");
        }
        ESignatureProvider provider=providers.byCode(providerCode);
        ESignatureProvider.VerifiedCallback callback;
        try { callback=provider.verifyWebhook(body,headers,clock.instant()); }
        catch(SecurityException ex){throw new ApiException(HttpStatus.UNAUTHORIZED,"WEBHOOK_SIGNATURE_INVALID","Contract webhook signature is invalid.");}
        catch(IllegalArgumentException ex){throw new ApiException(HttpStatus.BAD_REQUEST,"WEBHOOK_PAYLOAD_INVALID","Contract webhook payload is invalid.");}
        String hash=CryptoSupport.sha256Hex(body);
        var claim=require(tx.execute(status->webhookLedger.claim(callback.organizationId(),provider.providerCode(),callback.eventId(),callback.eventType(),hash,clock.instant())));
        if(!claim.process())return new WebhookResult(true,claim.existingStatus());
        try {
            Plan plan=require(tx.execute(status->applyVerified(provider.providerCode(),callback)));
            if(!plan.needsSignedDocument())return new WebhookResult(false,plan.status());
            if(!storage.available())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_DOCUMENT_STORAGE_UNAVAILABLE","Signed-contract storage is temporarily unavailable.");
            ESignatureProvider.SignedDocument document;
            try{document=provider.downloadSignedDocument(callback.providerDocumentId());}
            catch(RuntimeException ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_PROVIDER_UNAVAILABLE","The signed document could not be retrieved from the e-signature provider.");}
            byte[] bytes=document.bytes();
            if(bytes.length<=0||bytes.length>properties.maxSignedDocumentBytes())throw new ApiException(HttpStatus.BAD_REQUEST,"SIGNED_DOCUMENT_INVALID","Signed contract document size is invalid.");
            if(!"application/pdf".equalsIgnoreCase(document.contentType()) || !looksLikePdf(bytes))
                throw new ApiException(HttpStatus.BAD_REQUEST,"SIGNED_DOCUMENT_INVALID","Signed contract document must be a valid PDF artifact.");
            String objectKey="contracts/"+callback.organizationId()+"/"+plan.projectId()+"/"+callback.contractId()+"/"+callback.contractVersionId()+"/signed.pdf";
            ContractDocumentStorage.StoredDocument stored;
            try{stored=storage.storeSignedPdf(objectKey,bytes);}catch(IllegalArgumentException ex){throw new ApiException(HttpStatus.BAD_REQUEST,"SIGNED_DOCUMENT_INVALID",ex.getMessage());}catch(RuntimeException ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_DOCUMENT_STORAGE_UNAVAILABLE","Signed-contract storage is temporarily unavailable.");}
            require(tx.execute(status->{finalizeSigned(provider.providerCode(),callback,stored);return Boolean.TRUE;}));
            return new WebhookResult(false,"PROCESSED");
        } catch(RuntimeException ex) {
            tx.executeWithoutResult(status->webhookLedger.failed(provider.providerCode(),callback.eventId(),safeError(ex),clock.instant()));
            throw ex;
        }
    }

    private Plan applyVerified(String provider, ESignatureProvider.VerifiedCallback callback) {
        Contract contract=contracts.findForUpdate(callback.organizationId(),callback.contractId()).orElseThrow(ContractWebhookService::notFound);
        ContractVersion version=versions.findByOrganizationIdAndId(callback.organizationId(),callback.contractVersionId()).orElseThrow(ContractWebhookService::notFound);
        validateReferences(contract,version,provider,callback);
        String type=normalizeType(callback.eventType());
        Instant at=callback.occurredAt()==null?clock.instant():callback.occurredAt();
        switch(type){
            case "CONTRACT_VIEWED" -> { contract.providerViewed(at); markRecipientViewed(callback,at); contracts.saveAndFlush(contract); recipients.flush(); completeLedger(provider,callback,"CONTRACT_VIEWED",contract); return new Plan(false,"PROCESSED",contract.getProjectId()); }
            case "RECIPIENT_VIEWED" -> { markRecipientViewedRequired(callback,at); recipients.flush(); if(contract.getStatus()==ContractStatus.SENT){contract.providerViewed(at);contracts.saveAndFlush(contract);} completeLedger(provider,callback,"CONTRACT_VIEWED",contract); return new Plan(false,"PROCESSED",contract.getProjectId()); }
            case "RECIPIENT_SIGNED" -> { markRecipientSigned(provider,callback,at); recipients.flush(); signatures.flush(); completeLedger(provider,callback,"CONTRACT_RECIPIENT_SIGNED",contract); return new Plan(false,"PROCESSED",contract.getProjectId()); }
            case "RECIPIENT_DECLINED","CONTRACT_DECLINED" -> { markRecipientDeclined(callback,at); if(contract.getStatus()==ContractStatus.SENT||contract.getStatus()==ContractStatus.VIEWED){contract.declined(at);contracts.saveAndFlush(contract);} completeLedger(provider,callback,"CONTRACT_DECLINED",contract); return new Plan(false,"PROCESSED",contract.getProjectId()); }
            case "CONTRACT_EXPIRED" -> { if(contract.getStatus()==ContractStatus.SENT||contract.getStatus()==ContractStatus.VIEWED){contract.expired(at);contracts.saveAndFlush(contract);} completeLedger(provider,callback,"CONTRACT_EXPIRED",contract); return new Plan(false,"PROCESSED",contract.getProjectId()); }
            case "CONTRACT_SIGNED" -> {
                if(callback.recipientEmail()!=null) markRecipientSigned(provider,callback,at);
                recipients.flush(); signatures.flush();
                long remaining = recipients.countByOrganizationIdAndContractIdAndStatusNot(
                        callback.organizationId(), callback.contractId(), ContractRecipientStatus.SIGNED);
                if (remaining != 0) {
                    throw new ApiException(HttpStatus.CONFLICT,"CONTRACT_SIGNATURES_INCOMPLETE",
                            "The provider marked the contract complete before all configured recipients were recorded as signed.");
                }
                return new Plan(true,"RECEIVED",contract.getProjectId());
            }
            default -> { webhookLedger.ignored(provider,callback.eventId(),clock.instant()); return new Plan(false,"IGNORED",contract.getProjectId()); }
        }
    }

    private void finalizeSigned(String provider, ESignatureProvider.VerifiedCallback callback,
                                ContractDocumentStorage.StoredDocument stored) {
        Contract contract=contracts.findForUpdate(callback.organizationId(),callback.contractId()).orElseThrow(ContractWebhookService::notFound);
        ContractVersion version=versions.findByOrganizationIdAndId(callback.organizationId(),callback.contractVersionId()).orElseThrow(ContractWebhookService::notFound);
        validateReferences(contract,version,provider,callback);
        long remaining=recipients.countByOrganizationIdAndContractIdAndStatusNot(callback.organizationId(),callback.contractId(),ContractRecipientStatus.SIGNED);
        if(remaining!=0)throw new ApiException(HttpStatus.CONFLICT,"CONTRACT_SIGNATURES_INCOMPLETE","The provider marked the contract complete before all configured recipients were recorded as signed.");
        if(contract.getStatus()!=ContractStatus.SENT&&contract.getStatus()!=ContractStatus.VIEWED&&contract.getStatus()!=ContractStatus.SIGNED)throw new ApiException(HttpStatus.CONFLICT,"CONTRACT_STATE_INVALID","The contract cannot accept a signed callback in its current state.");
        version.storeSignedDocument(stored.bucket(),stored.key(),stored.sha256(),stored.sizeBytes(),stored.contentType(),stored.storedAt());
        versions.saveAndFlush(version);
        if(contract.getStatus()!=ContractStatus.SIGNED){contract.signed(callback.occurredAt()==null?clock.instant():callback.occurredAt());contracts.saveAndFlush(contract);}
        webhookLedger.processed(provider,callback.eventId(),clock.instant());
        audit.recordApplication(callback.organizationId(),null,"PROVIDER","CONTRACT_SIGNED","CONTRACT",contract.getId(),null,
                Map.of("provider",provider,"providerEventId",callback.eventId(),"providerDocumentId",callback.providerDocumentId(),"signedDocumentSha256",stored.sha256()));
        activity.recordSystem(callback.organizationId(),contract.getClientId(),contract.getProjectId(),"CONTRACT_SIGNED","CONTRACT",contract.getId(),"Contract signed",Map.of("provider",provider));
        outbox.record(callback.organizationId(),"CONTRACT_SIGNED","CONTRACT",contract.getId(),Map.of("contractId",contract.getId(),"projectId",contract.getProjectId(),"clientId",contract.getClientId(),"signedDocumentSha256",stored.sha256()));
        events.publishEvent(new ContractSignedEvent(callback.organizationId(),contract.getId(),contract.getOnboardingId(),contract.getStepInstanceId()));
    }

    private void completeLedger(String provider,ESignatureProvider.VerifiedCallback callback,String eventName,Contract contract){
        webhookLedger.processed(provider,callback.eventId(),clock.instant());
        audit.recordApplication(callback.organizationId(),null,"PROVIDER",eventName,"CONTRACT",contract.getId(),null,Map.of("provider",provider,"providerEventId",callback.eventId()));
        outbox.record(callback.organizationId(),eventName,"CONTRACT",contract.getId(),Map.of("contractId",contract.getId(),"projectId",contract.getProjectId(),"providerEventId",callback.eventId()));
    }
    private void markRecipientViewed(ESignatureProvider.VerifiedCallback c,Instant at){if(c.recipientEmail()==null)return;recipients.findByOrganizationIdAndContractIdAndNormalizedEmail(c.organizationId(),c.contractId(),normalizeEmail(c.recipientEmail())).ifPresent(r->r.viewed(at));}
    private void markRecipientViewedRequired(ESignatureProvider.VerifiedCallback c,Instant at){recipient(c).viewed(at);}
    private void markRecipientDeclined(ESignatureProvider.VerifiedCallback c,Instant at){if(c.recipientEmail()!=null)recipient(c).declined(at);}
    private void markRecipientSigned(String provider,ESignatureProvider.VerifiedCallback c,Instant at){ContractRecipient r=recipient(c);r.signed(at);if(!signatures.existsByProviderAndProviderEventId(provider,c.eventId()))signatures.save(new ContractSignature(UUID.randomUUID(),c.organizationId(),c.contractId(),c.contractVersionId(),r.getId(),provider,c.eventId(),c.providerSignatureId(),blank(c.signatoryName())?r.getDisplayName():c.signatoryName().trim(),r.getEmail(),at,clock.instant()));}
    private ContractRecipient recipient(ESignatureProvider.VerifiedCallback c){if(blank(c.recipientEmail()))throw new ApiException(HttpStatus.BAD_REQUEST,"WEBHOOK_PAYLOAD_INVALID","Recipient email is required for this contract event.");return recipients.findByOrganizationIdAndContractIdAndNormalizedEmail(c.organizationId(),c.contractId(),normalizeEmail(c.recipientEmail())).orElseThrow(ContractWebhookService::notFound);}
    private static void validateReferences(Contract contract,ContractVersion version,String provider,ESignatureProvider.VerifiedCallback c){if(!version.getContractId().equals(contract.getId())||!contract.getProjectId().equals(version.getProjectId())||contract.getProvider()==null||!contract.getProvider().equalsIgnoreCase(provider)||!c.providerDocumentId().equals(contract.getProviderDocumentId())||version.getProviderDocumentId()==null||!c.providerDocumentId().equals(version.getProviderDocumentId()))throw notFound();}

    private static boolean looksLikePdf(byte[] bytes) {
        return bytes != null && bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    private static String normalizeType(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT).replace('.','_').replace('-','_');}
    private static String normalizeEmail(String value){return value.trim().toLowerCase(Locale.ROOT);}
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static String safeError(Throwable ex){String v=ex.getMessage();if(v==null||v.isBlank())v=ex.getClass().getSimpleName();return v.substring(0,Math.min(v.length(),2000));}
    private static <T>T require(T value){if(value==null)throw new IllegalStateException("Transaction returned no result");return value;}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Referenced contract resource was not found.");}
    private record Plan(boolean needsSignedDocument,String status,UUID projectId){}
    public record WebhookResult(boolean duplicate,String status){}
}
