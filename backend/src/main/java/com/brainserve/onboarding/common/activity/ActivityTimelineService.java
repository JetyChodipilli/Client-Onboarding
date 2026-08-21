package com.brainserve.onboarding.common.activity;

import com.brainserve.onboarding.common.api.ActivityResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared append-oriented operational timeline for tenant-owned client/project activity. */
@Service
public class ActivityTimelineService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ActivityTimelineService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(UUID organizationId, UUID clientId, UUID projectId, UUID actorUserId,
                       String action, String entityType, UUID entityId, String summary, Map<String, ?> metadata) {
        record(organizationId, clientId, projectId, actorUserId, "INTERNAL", action, entityType, entityId, summary, metadata);
    }

    public void recordClient(UUID organizationId, UUID clientId, UUID projectId, UUID actorUserId,
                             String action, String entityType, UUID entityId, String summary, Map<String, ?> metadata) {
        record(organizationId, clientId, projectId, actorUserId, "CLIENT", action, entityType, entityId, summary, metadata);
    }

    public void recordSystem(UUID organizationId, UUID clientId, UUID projectId,
                             String action, String entityType, UUID entityId, String summary, Map<String, ?> metadata) {
        record(organizationId, clientId, projectId, null, "SYSTEM", action, entityType, entityId, summary, metadata);
    }

    private void record(UUID organizationId, UUID clientId, UUID projectId, UUID actorUserId, String actorType,
                        String action, String entityType, UUID entityId, String summary, Map<String, ?> metadata) {
        jdbc.update("""
                INSERT INTO client_onboarding.activity_logs
                    (id, organization_id, client_id, project_id, actor_user_id, actor_type, action, entity_type,
                     entity_id, summary, metadata, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, UUID.randomUUID(), organizationId, clientId, projectId, actorUserId, actorType, action, entityType,
                entityId, summary, json(metadata), clock.instant());
    }

    public PageResult listByProject(UUID organizationId, UUID projectId, int page, int size) {
        return list(organizationId, "project_id", projectId, page, size);
    }

    public PageResult listByClient(UUID organizationId, UUID clientId, int page, int size) {
        return list(organizationId, "client_id", clientId, page, size);
    }

    private PageResult list(UUID organizationId, String scopeColumn, UUID scopeId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = Math.multiplyExact((long) safePage, safeSize);
        String countSql = "SELECT COUNT(*) FROM client_onboarding.activity_logs WHERE organization_id = ? AND " + scopeColumn + " = ?";
        String querySql = """
                SELECT id, actor_user_id, action, entity_type, entity_id, summary, metadata, occurred_at
                FROM client_onboarding.activity_logs
                WHERE organization_id = ? AND %s = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(scopeColumn);
        Long total = jdbc.queryForObject(countSql, Long.class, organizationId, scopeId);
        List<ActivityResponse> items = jdbc.query(querySql, (rs, rowNum) -> new ActivityResponse(
                rs.getObject("id", UUID.class), rs.getObject("actor_user_id", UUID.class),
                rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                rs.getString("summary"), parseJson(rs.getString("metadata")), rs.getTimestamp("occurred_at").toInstant()),
                organizationId, scopeId, safeSize, offset);
        long count = total == null ? 0L : total;
        int pages = count == 0 ? 0 : Math.toIntExact((count + safeSize - 1) / safeSize);
        return new PageResult(items, safePage, safeSize, count, pages);
    }

    private String json(Map<String, ?> value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Activity metadata could not be serialized", ex);
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored activity metadata is invalid", ex);
        }
    }

    public record PageResult(List<ActivityResponse> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
