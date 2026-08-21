package com.brainserve.onboarding.contracts.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name="contract_recipients",schema="client_onboarding")
public class ContractRecipient {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="contract_id",nullable=false) private UUID contractId;
    @Column(name="client_id",nullable=false) private UUID clientId;
    @Column(name="contact_id") private UUID contactId;
    @Column(name="display_name",nullable=false,length=160) private String displayName;
    @Column(nullable=false,length=320) private String email;
    @Column(name="normalized_email",nullable=false,length=320) private String normalizedEmail;
    @Column(name="signing_order",nullable=false) private int signingOrder;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ContractRecipientStatus status;
    @Column(name="provider_recipient_id",length=180) private String providerRecipientId;
    @Column(name="viewed_at") private Instant viewedAt;
    @Column(name="signed_at") private Instant signedAt;
    @Column(name="declined_at") private Instant declinedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    protected ContractRecipient() {}
    public ContractRecipient(UUID id,UUID org,UUID contractId,UUID clientId,UUID contactId,String displayName,String email,int order,UUID actor,Instant now){this.id=id;this.organizationId=org;this.contractId=contractId;this.clientId=clientId;this.contactId=contactId;this.displayName=text(displayName,"Recipient name",160);this.email=text(email,"Recipient email",320);this.normalizedEmail=this.email.toLowerCase(Locale.ROOT);if(order<0)throw new IllegalArgumentException("Signing order cannot be negative");this.signingOrder=order;this.status=ContractRecipientStatus.PENDING;this.createdAt=now;this.createdBy=actor;}
    public void sent(String providerRecipientId){if(status==ContractRecipientStatus.PENDING)status=ContractRecipientStatus.SENT;this.providerRecipientId=providerRecipientId==null?this.providerRecipientId:text(providerRecipientId,"Provider recipient id",180);}
    public void viewed(Instant now){if(status==ContractRecipientStatus.SENT){status=ContractRecipientStatus.VIEWED;viewedAt=now;}}
    public void signed(Instant now){if(status==ContractRecipientStatus.SENT||status==ContractRecipientStatus.VIEWED){status=ContractRecipientStatus.SIGNED;signedAt=now;}}
    public void declined(Instant now){if(status==ContractRecipientStatus.SENT||status==ContractRecipientStatus.VIEWED){status=ContractRecipientStatus.DECLINED;declinedAt=now;}}
    private static String text(String v,String l,int m){if(v==null||v.isBlank())throw new IllegalArgumentException(l+" is required");String n=v.trim();if(n.length()>m)throw new IllegalArgumentException(l+" is too long");return n;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getContractId(){return contractId;} public UUID getClientId(){return clientId;} public UUID getContactId(){return contactId;} public String getDisplayName(){return displayName;} public String getEmail(){return email;} public String getNormalizedEmail(){return normalizedEmail;} public int getSigningOrder(){return signingOrder;} public ContractRecipientStatus getStatus(){return status;} public String getProviderRecipientId(){return providerRecipientId;} public Instant getViewedAt(){return viewedAt;} public Instant getSignedAt(){return signedAt;} public Instant getDeclinedAt(){return declinedAt;}
}
