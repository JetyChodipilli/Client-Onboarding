package com.brainserve.onboarding.contracts.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="contracts", schema="client_onboarding")
public class Contract {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="project_id",nullable=false) private UUID projectId;
    @Column(name="client_id",nullable=false) private UUID clientId;
    @Column(name="onboarding_id") private UUID onboardingId;
    @Column(name="step_instance_id") private UUID stepInstanceId;
    @Column(name="template_id",nullable=false) private UUID templateId;
    @Column(name="template_version_id",nullable=false) private UUID templateVersionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private ContractStatus status;
    @Column(nullable=false,length=240) private String subject;
    @Column(length=40) private String provider;
    @Column(name="provider_document_id",length=180) private String providerDocumentId;
    @Column(name="provider_signing_url",length=2000) private String providerSigningUrl;
    @Column(name="send_idempotency_key",length=128) private String sendIdempotencyKey;
    @Column(name="send_reserved_at") private Instant sendReservedAt;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="generated_at") private Instant generatedAt;
    @Column(name="sent_at") private Instant sentAt;
    @Column(name="viewed_at") private Instant viewedAt;
    @Column(name="signed_at") private Instant signedAt;
    @Column(name="declined_at") private Instant declinedAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="creation_idempotency_key",nullable=false,length=128) private String creationIdempotencyKey;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="updated_by") private UUID updatedBy;
    @Enumerated(EnumType.STRING) @Column(name="updated_by_type",nullable=false,length=16) private ContractActorType updatedByType;
    @Version private long version;
    protected Contract() {}
    public Contract(UUID id,UUID org,UUID projectId,UUID clientId,UUID onboardingId,UUID stepId,UUID templateId,UUID templateVersionId,String subject,Instant expiresAt,String creationKey,UUID actor,Instant now){this.id=id;this.organizationId=org;this.projectId=projectId;this.clientId=clientId;this.onboardingId=onboardingId;this.stepInstanceId=stepId;this.templateId=templateId;this.templateVersionId=templateVersionId;this.subject=text(subject,"Contract subject",240);this.expiresAt=expiresAt;this.creationIdempotencyKey=text(creationKey,"Idempotency key",128);this.status=ContractStatus.DRAFT;this.createdAt=now;this.createdBy=actor;touch(ContractActorType.INTERNAL,actor,now);}
    public void generated(UUID actor,Instant now){transition(ContractStatus.GENERATED,ContractActorType.INTERNAL,actor,now);generatedAt=now;}
    public void reserveSend(String sendKey, UUID actor, Instant now) {
        if (status != ContractStatus.GENERATED) throw new IllegalStateException("Only generated contracts can be sent");
        String normalized = text(sendKey, "Send idempotency key", 128);
        if (sendIdempotencyKey != null && !sendIdempotencyKey.equals(normalized)) {
            throw new IllegalStateException("This contract already has a different send operation in progress");
        }
        if (sendIdempotencyKey == null) { sendIdempotencyKey = normalized; sendReservedAt = now; touch(ContractActorType.INTERNAL, actor, now); }
    }
    public void sent(String provider,String providerDocumentId,String signingUrl,String sendKey,Instant sentAt,Instant expiresAt,UUID actor){if(status==ContractStatus.SENT&&sendKey!=null&&sendKey.equals(sendIdempotencyKey))return;if(status!=ContractStatus.GENERATED)throw new IllegalStateException("Only generated contracts can be sent");String normalized=text(sendKey,"Send idempotency key",128);if(sendIdempotencyKey==null||!sendIdempotencyKey.equals(normalized))throw new IllegalStateException("Send operation does not match the reserved Idempotency-Key");transition(ContractStatus.SENT,ContractActorType.INTERNAL,actor,sentAt);this.provider=text(provider,"Provider",40);this.providerDocumentId=text(providerDocumentId,"Provider document id",180);this.providerSigningUrl=text(signingUrl,"Signing URL",2000);this.sentAt=sentAt;this.expiresAt=expiresAt;}
    public void markViewed(UUID actor,Instant now){if(status==ContractStatus.SENT){transition(ContractStatus.VIEWED,ContractActorType.CLIENT,actor,now);viewedAt=now;}}
    public void providerViewed(Instant now){if(status==ContractStatus.SENT){transition(ContractStatus.VIEWED,ContractActorType.PROVIDER,null,now);viewedAt=now;}}
    public void signed(Instant now){if(status==ContractStatus.SENT||status==ContractStatus.VIEWED){transition(ContractStatus.SIGNED,ContractActorType.PROVIDER,null,now);signedAt=now;}}
    public void declined(Instant now){if(status==ContractStatus.SENT||status==ContractStatus.VIEWED){transition(ContractStatus.DECLINED,ContractActorType.PROVIDER,null,now);declinedAt=now;}}
    public void expired(Instant now){if(status==ContractStatus.SENT||status==ContractStatus.VIEWED){transition(ContractStatus.EXPIRED,ContractActorType.PROVIDER,null,now);}}
    public void cancel(UUID actor,Instant now){transition(ContractStatus.CANCELLED,ContractActorType.INTERNAL,actor,now);cancelledAt=now;}
    public void voidContract(UUID actor,Instant now){transition(ContractStatus.VOID,ContractActorType.INTERNAL,actor,now);cancelledAt=now;}
    private void transition(ContractStatus target,ContractActorType type,UUID actor,Instant now){if(!ContractStateMachine.canTransition(status,target))throw new IllegalStateException("Contract cannot transition from "+status+" to "+target);status=target;touch(type,actor,now);}
    private void touch(ContractActorType type,UUID actor,Instant now){if((type==ContractActorType.INTERNAL||type==ContractActorType.CLIENT)&&actor==null)throw new IllegalArgumentException("Human contract actor is required");updatedByType=type;updatedBy=(type==ContractActorType.INTERNAL||type==ContractActorType.CLIENT)?actor:null;updatedAt=now;}
    private static String text(String v,String l,int m){if(v==null||v.isBlank())throw new IllegalArgumentException(l+" is required");String n=v.trim();if(n.length()>m)throw new IllegalArgumentException(l+" is too long");return n;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getProjectId(){return projectId;} public UUID getClientId(){return clientId;} public UUID getOnboardingId(){return onboardingId;} public UUID getStepInstanceId(){return stepInstanceId;} public UUID getTemplateId(){return templateId;} public UUID getTemplateVersionId(){return templateVersionId;} public ContractStatus getStatus(){return status;} public String getSubject(){return subject;} public String getProvider(){return provider;} public String getProviderDocumentId(){return providerDocumentId;} public String getProviderSigningUrl(){return providerSigningUrl;} public String getSendIdempotencyKey(){return sendIdempotencyKey;} public Instant getSendReservedAt(){return sendReservedAt;} public Instant getExpiresAt(){return expiresAt;} public Instant getGeneratedAt(){return generatedAt;} public Instant getSentAt(){return sentAt;} public Instant getViewedAt(){return viewedAt;} public Instant getSignedAt(){return signedAt;} public Instant getDeclinedAt(){return declinedAt;} public Instant getCancelledAt(){return cancelledAt;} public Instant getCreatedAt(){return createdAt;} public long getVersion(){return version;}
}
