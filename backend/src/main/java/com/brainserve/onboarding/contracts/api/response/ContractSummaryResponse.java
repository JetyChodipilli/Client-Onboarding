package com.brainserve.onboarding.contracts.api.response;
import com.brainserve.onboarding.contracts.domain.model.ContractStatus;import java.time.Instant;import java.util.UUID;
public record ContractSummaryResponse(UUID id,UUID projectId,String subject,ContractStatus status,Instant expiresAt,Instant sentAt,Instant signedAt,Instant createdAt,long version){}
