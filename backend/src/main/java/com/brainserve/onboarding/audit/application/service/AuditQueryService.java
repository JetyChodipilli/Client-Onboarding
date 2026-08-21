package com.brainserve.onboarding.audit.application.service;

import com.brainserve.onboarding.audit.api.response.AuditLogResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResult list(UUID organizationId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_onboarding.audit_logs WHERE organization_id = ?",
                Long.class, organizationId);
        List<AuditLogResponse> items = jdbc.query("""
                SELECT id, actor_user_id, action, entity_type, entity_id, before_state, after_state,
                       request_id, correlation_id, occurred_at
                FROM client_onboarding.audit_logs
                WHERE organization_id = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new AuditLogResponse(
                        rs.getObject("id", UUID.class), rs.getObject("actor_user_id", UUID.class),
                        rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                        json(rs.getString("before_state")), json(rs.getString("after_state")),
                        rs.getString("request_id"), rs.getString("correlation_id"), rs.getTimestamp("occurred_at").toInstant()),
                organizationId, safeSize, (long) safePage * safeSize);
        long count = total == null ? 0 : total;
        return new PageResult(items, safePage, safeSize, count,
                safeSize == 0 ? 0 : (int) Math.ceil((double) count / safeSize));
    }

    private JsonNode json(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored audit JSON is invalid", ex);
        }
    }

    public record PageResult(List<AuditLogResponse> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
