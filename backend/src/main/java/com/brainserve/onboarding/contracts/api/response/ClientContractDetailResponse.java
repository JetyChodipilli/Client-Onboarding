package com.brainserve.onboarding.contracts.api.response;

import com.brainserve.onboarding.contracts.domain.model.ContractStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClientContractDetailResponse(
        UUID id,
        UUID projectId,
        String subject,
        ContractStatus status,
        String title,
        String legalContent,
        String contentHash,
        Instant expiresAt,
        Instant sentAt,
        Instant viewedAt,
        Instant signedAt,
        String yourSigningUrl,
        boolean signedDocumentAvailable,
        List<ContractRecipientResponse> recipients,
        long version) {
    public ClientContractDetailResponse { recipients = List.copyOf(recipients); }
}
