package com.brainserve.clientonboarding.audit.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.audit.domain.model.AuditEntry;
import com.brainserve.clientonboarding.audit.domain.repository.AuditRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditRepository implements AuditRepository {
    private final JdbcClient jdbc;

    public JdbcAuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEntry entry) {
        jdbc.sql("""
                INSERT INTO audit_logs (id, organization_id, actor_user_id, action, entity_type,
                    entity_id, source, before_state, after_state, request_id, correlation_id, ip_hash, created_at)
                VALUES (:id, :organizationId, :actorUserId, :action, :entityType, :entityId, :source,
                    :beforeState, :afterState, :requestId, :correlationId, :ipHash, :createdAt)
                """).param("id", entry.id()).param("organizationId", entry.organizationId())
                .param("actorUserId", entry.actorUserId()).param("action", entry.action())
                .param("entityType", entry.entityType()).param("entityId", entry.entityId())
                .param("source", entry.source()).param("beforeState", entry.beforeState())
                .param("afterState", entry.afterState()).param("requestId", entry.requestId())
                .param("correlationId", entry.correlationId()).param("ipHash", entry.ipHash())
                .param("createdAt", timestamp(entry.createdAt())).update();
    }

    @Override
    public List<AuditEntry> findPage(UUID organizationId, int limit, int offset) {
        return jdbc.sql("""
                SELECT * FROM audit_logs WHERE organization_id = :organizationId
                ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
                """).param("organizationId", organizationId).param("limit", limit).param("offset", offset)
                .query(this::map).list();
    }

    @Override
    public long count(UUID organizationId) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE organization_id = :organizationId")
                .param("organizationId", organizationId).query(Long.class).single();
    }

    private AuditEntry map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuditEntry(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class), rs.getString("action"), rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getString("source"), rs.getString("before_state"),
                rs.getString("after_state"), rs.getObject("request_id", UUID.class),
                rs.getObject("correlation_id", UUID.class), rs.getString("ip_hash"), instant(rs, "created_at"));
    }
}
