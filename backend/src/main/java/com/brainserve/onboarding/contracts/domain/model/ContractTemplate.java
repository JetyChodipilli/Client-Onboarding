package com.brainserve.onboarding.contracts.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="contract_templates", schema="client_onboarding")
public class ContractTemplate {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(nullable=false,length=180) private String name;
    @Column(length=2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ContractTemplateStatus status;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Version private long version;
    protected ContractTemplate() {}
    public ContractTemplate(UUID id,UUID org,String name,String description,UUID actor,Instant now){this.id=id;this.organizationId=org;this.name=text(name,"Template name",180);this.description=optional(description,2000);this.status=ContractTemplateStatus.ACTIVE;this.createdAt=now;this.createdBy=actor;this.updatedAt=now;this.updatedBy=actor;}
    public void update(String name,String description,UUID actor,Instant now){if(status==ContractTemplateStatus.ARCHIVED)throw new IllegalStateException("Archived contract templates cannot be changed");this.name=text(name,"Template name",180);this.description=optional(description,2000);touch(actor,now);}
    public void archive(UUID actor,Instant now){status=ContractTemplateStatus.ARCHIVED;touch(actor,now);}
    private void touch(UUID actor,Instant now){updatedBy=actor;updatedAt=now;}
    private static String text(String v,String l,int m){if(v==null||v.isBlank())throw new IllegalArgumentException(l+" is required");String n=v.trim();if(n.length()>m)throw new IllegalArgumentException(l+" is too long");return n;}
    private static String optional(String v,int m){if(v==null||v.isBlank())return null;String n=v.trim();if(n.length()>m)throw new IllegalArgumentException("Value is too long");return n;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public String getName(){return name;} public String getDescription(){return description;} public ContractTemplateStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
