package com.brainserve.onboarding.audit.api.response;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
public record AuditLogResponse(UUID id, UUID actorUserId, String action, String entityType, UUID entityId,
                               JsonNode beforeState, JsonNode afterState, String requestId, String correlationId,
                               Instant occurredAt) {}
