package com.brainserve.onboarding.contracts.api.response;
import com.brainserve.onboarding.contracts.domain.model.ContractTemplateVersionStatus;import java.time.Instant;import java.util.UUID;
public record ContractTemplateVersionResponse(UUID id,int versionNumber,ContractTemplateVersionStatus status,String title,String legalContent,String contentHash,Instant publishedAt,Instant createdAt,Instant updatedAt,long version){}
