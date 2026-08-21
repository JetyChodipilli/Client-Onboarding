package com.brainserve.onboarding.contracts.api.response;
import com.brainserve.onboarding.contracts.domain.model.ContractRecipientStatus;import java.time.Instant;import java.util.UUID;
public record ContractRecipientResponse(UUID id,UUID contactId,String displayName,String email,int signingOrder,ContractRecipientStatus status,Instant viewedAt,Instant signedAt,Instant declinedAt){}
