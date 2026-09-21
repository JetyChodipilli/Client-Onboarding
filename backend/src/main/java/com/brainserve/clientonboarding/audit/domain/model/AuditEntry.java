package com.brainserve.clientonboarding.audit.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        UUID organizationId,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String source,
        String beforeState,
        String afterState,
        UUID requestId,
        UUID correlationId,
        String ipHash,
        Instant createdAt
) { }
