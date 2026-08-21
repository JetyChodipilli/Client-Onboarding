package com.brainserve.onboarding.contracts.api.response;

import com.brainserve.onboarding.contracts.domain.model.ContractStatus;
import java.time.Instant;
import java.util.UUID;

public record ClientContractSummaryResponse(
        UUID id,
        UUID projectId,
        String subject,
        ContractStatus status,
        Instant expiresAt,
        Instant sentAt,
        Instant signedAt,
        boolean yourSignatureRequired) {}
