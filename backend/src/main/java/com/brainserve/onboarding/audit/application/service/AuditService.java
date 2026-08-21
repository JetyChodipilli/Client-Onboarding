package com.brainserve.onboarding.audit.application.service;

import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.NetworkSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(UUID organizationId, UUID actorUserId, String action, String entityType,
                       UUID entityId, Object beforeState, Object afterState, HttpServletRequest request) {
        record(organizationId, actorUserId, "INTERNAL", action, entityType, entityId,
                beforeState, afterState, "API", request == null ? null : NetworkSource.clientIp(request));
    }

    public void recordClient(UUID organizationId, UUID actorUserId, String action, String entityType,
                             UUID entityId, Object beforeState, Object afterState, HttpServletRequest request) {
        record(organizationId, actorUserId, "CLIENT", action, entityType, entityId,
                beforeState, afterState, "API", request == null ? null : NetworkSource.clientIp(request));
    }

    /** Records a state transition performed through an application boundary rather than a controller. */
    public void recordApplication(UUID organizationId, UUID actorUserId, String actorType, String action,
                                  String entityType, UUID entityId, Object beforeState, Object afterState) {
        record(organizationId, actorUserId, actorType, action, entityType, entityId,
                beforeState, afterState, "APPLICATION", null);
    }

    private void record(UUID organizationId, UUID actorUserId, String actorType, String action,
                        String entityType, UUID entityId, Object beforeState, Object afterState,
                        String source, String ipAddress) {
        jdbc.update("""
                INSERT INTO client_onboarding.audit_logs
                    (id, organization_id, actor_user_id, actor_type, action, entity_type, entity_id, source,
                     before_state, after_state, request_id, correlation_id, ip_address, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
                """, UUID.randomUUID(), organizationId, actorUserId, actorType, action, entityType, entityId,
                source, json(beforeState), json(afterState), RequestContext.requestId(), RequestContext.correlationId(),
                ipAddress, clock.instant());
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Audit state could not be serialized", ex); }
    }
}
