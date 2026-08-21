package com.brainserve.onboarding.contracts.domain.model;

import com.brainserve.onboarding.common.util.CryptoSupport;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="contract_template_versions", schema="client_onboarding")
public class ContractTemplateVersion {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="template_id",nullable=false) private UUID templateId;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ContractTemplateVersionStatus status;
    @Column(nullable=false,length=240) private String title;
    @Column(name="legal_content",nullable=false,columnDefinition="text") private String legalContent;
    @Column(name="content_hash",nullable=false,length=64) private String contentHash;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="published_by") private UUID publishedBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Version private long version;
    protected ContractTemplateVersion() {}
    public ContractTemplateVersion(UUID id,UUID org,UUID templateId,int versionNumber,String title,String content,UUID actor,Instant now){this.id=id;this.organizationId=org;this.templateId=templateId;if(versionNumber<=0)throw new IllegalArgumentException("Version number must be positive");this.versionNumber=versionNumber;this.status=ContractTemplateVersionStatus.DRAFT;replace(title,content,actor,now);this.createdAt=now;this.createdBy=actor;}
    public void replace(String title,String content,UUID actor,Instant now){if(status!=ContractTemplateVersionStatus.DRAFT)throw new IllegalStateException("Only draft contract template versions can be edited");this.title=text(title,"Contract title",240);this.legalContent=text(content,"Legal content",200_000);this.contentHash=CryptoSupport.sha256Hex(this.legalContent);this.updatedAt=now;this.updatedBy=actor;}
    public void publish(UUID actor,Instant now){if(status!=ContractTemplateVersionStatus.DRAFT)throw new IllegalStateException("Only draft versions can be published");status=ContractTemplateVersionStatus.PUBLISHED;publishedAt=now;publishedBy=actor;updatedAt=now;updatedBy=actor;}
    private static String text(String v,String l,int m){if(v==null||v.isBlank())throw new IllegalArgumentException(l+" is required");String n=v.trim();if(n.length()>m)throw new IllegalArgumentException(l+" is too long");return n;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getTemplateId(){return templateId;} public int getVersionNumber(){return versionNumber;} public ContractTemplateVersionStatus getStatus(){return status;} public String getTitle(){return title;} public String getLegalContent(){return legalContent;} public String getContentHash(){return contentHash;} public Instant getPublishedAt(){return publishedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
