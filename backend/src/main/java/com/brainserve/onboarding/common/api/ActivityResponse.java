package com.brainserve.onboarding.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String summary,
        JsonNode metadata,
        Instant occurredAt) {}
