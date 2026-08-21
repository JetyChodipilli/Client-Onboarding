package com.brainserve.onboarding.contracts.domain.model;

import com.brainserve.onboarding.common.util.CryptoSupport;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="contract_versions",schema="client_onboarding")
public class ContractVersion {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="contract_id",nullable=false) private UUID contractId;
    @Column(name="project_id",nullable=false) private UUID projectId;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Column(name="template_version_id",nullable=false) private UUID templateVersionId;
    @Column(nullable=false,length=240) private String title;
    @Column(name="legal_content",nullable=false,columnDefinition="text") private String legalContent;
    @Column(name="content_hash",nullable=false,length=64) private String contentHash;
    @Column(name="provider_document_id",length=180) private String providerDocumentId;
    @Column(name="sent_at") private Instant sentAt;
    @Column(name="signed_document_bucket",length=128) private String signedDocumentBucket;
    @Column(name="signed_document_key",length=1000) private String signedDocumentKey;
    @Column(name="signed_document_sha256",length=64) private String signedDocumentSha256;
    @Column(name="signed_document_size") private Long signedDocumentSize;
    @Column(name="signed_document_content_type",length=120) private String signedDocumentContentType;
    @Column(name="signed_document_stored_at") private Instant signedDocumentStoredAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    protected ContractVersion() {}
    public ContractVersion(UUID id,UUID org,UUID contractId,UUID projectId,int versionNumber,UUID templateVersionId,String title,String legalContent,UUID actor,Instant now){this.id=id;this.organizationId=org;this.contractId=contractId;this.projectId=projectId;if(versionNumber<=0)throw new IllegalArgumentException("Version number must be positive");this.versionNumber=versionNumber;this.templateVersionId=templateVersionId;this.title=text(title,"Contract title",240);this.legalContent=text(legalContent,"Legal content",200_000);this.contentHash=CryptoSupport.sha256Hex(this.legalContent);this.createdAt=now;this.createdBy=actor;}
    public void markSent(String providerDocumentId,Instant now){if(sentAt!=null&&providerDocumentId.equals(this.providerDocumentId))return;this.providerDocumentId=text(providerDocumentId,"Provider document id",180);this.sentAt=now;}
    public void storeSignedDocument(String bucket,String key,String sha256,long size,String contentType,Instant now){if(size<=0)throw new IllegalArgumentException("Signed document size must be positive");if(!"application/pdf".equals(contentType))throw new IllegalArgumentException("Signed contract must be a PDF");if(signedDocumentKey!=null&&(!signedDocumentKey.equals(key)||!java.util.Objects.equals(signedDocumentSha256,sha256)))throw new IllegalStateException("Signed contract document is already stored with different evidence");this.signedDocumentBucket=text(bucket,"Signed document bucket",128);this.signedDocumentKey=text(key,"Signed document key",1000);this.signedDocumentSha256=text(sha256,"Signed document hash",64);this.signedDocumentSize=size;this.signedDocumentContentType=contentType;this.signedDocumentStoredAt=now;}
    private static String text(String v,String l,int m){if(v==null||v.isBlank())throw new IllegalArgumentException(l+" is required");String n=v.trim();if(n.length()>m)throw new IllegalArgumentException(l+" is too long");return n;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getContractId(){return contractId;} public UUID getProjectId(){return projectId;} public int getVersionNumber(){return versionNumber;} public UUID getTemplateVersionId(){return templateVersionId;} public String getTitle(){return title;} public String getLegalContent(){return legalContent;} public String getContentHash(){return contentHash;} public String getProviderDocumentId(){return providerDocumentId;} public Instant getSentAt(){return sentAt;} public String getSignedDocumentBucket(){return signedDocumentBucket;} public String getSignedDocumentKey(){return signedDocumentKey;} public String getSignedDocumentSha256(){return signedDocumentSha256;} public Long getSignedDocumentSize(){return signedDocumentSize;} public String getSignedDocumentContentType(){return signedDocumentContentType;} public Instant getSignedDocumentStoredAt(){return signedDocumentStoredAt;} public Instant getCreatedAt(){return createdAt;}
}
