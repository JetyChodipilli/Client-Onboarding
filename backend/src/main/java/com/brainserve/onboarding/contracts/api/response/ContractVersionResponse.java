package com.brainserve.onboarding.contracts.api.response;
import java.time.Instant;import java.util.UUID;
public record ContractVersionResponse(UUID id,int versionNumber,UUID templateVersionId,String title,String legalContent,String contentHash,Instant sentAt,boolean signedDocumentAvailable,String signedDocumentSha256,Long signedDocumentSize,Instant signedDocumentStoredAt){}
