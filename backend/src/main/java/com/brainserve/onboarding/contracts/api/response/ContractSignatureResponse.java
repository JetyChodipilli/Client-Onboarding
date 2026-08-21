package com.brainserve.onboarding.contracts.api.response;
import java.time.Instant;import java.util.UUID;
public record ContractSignatureResponse(UUID id,UUID recipientId,String signatoryName,String signatoryEmail,Instant signedAt){}
