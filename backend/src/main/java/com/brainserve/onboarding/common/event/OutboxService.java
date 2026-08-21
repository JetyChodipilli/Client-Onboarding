package com.brainserve.onboarding.common.event;

import com.brainserve.onboarding.common.observability.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Transactional outbox writer. Publishing/worker behavior belongs to later async phases. */
@Service
public class OutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public UUID record(UUID organizationId, String eventType, String aggregateType, UUID aggregateId, Map<String, ?> payload) {
        UUID id = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        String json;
        try { json = objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Outbox payload is not serializable", ex); }
        jdbc.update("""
                INSERT INTO client_onboarding.outbox_events
                  (id, organization_id, event_type, aggregate_type, aggregate_id, correlation_id, payload_version, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, CAST(? AS jsonb), ?)
                """, id, organizationId, eventType, aggregateType, aggregateId, RequestContext.correlationId(), json, occurredAt);
        return id;
    }
}
