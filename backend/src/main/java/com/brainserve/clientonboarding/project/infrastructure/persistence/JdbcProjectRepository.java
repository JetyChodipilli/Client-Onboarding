package com.brainserve.clientonboarding.project.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.project.domain.model.ActivityEntry;
import com.brainserve.clientonboarding.project.domain.model.ProjectMember;
import com.brainserve.clientonboarding.project.domain.model.ProjectRecord;
import com.brainserve.clientonboarding.project.domain.repository.ProjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private static final String SELECT = """
            SELECT p.*, c.name client_name, c.status client_status, s.name service_name, s.code service_code
            FROM projects p
            JOIN clients c ON c.organization_id = p.organization_id AND c.id = p.client_id
            JOIN services s ON s.organization_id = p.organization_id AND s.id = p.service_id
            """;
    private final JdbcClient jdbc;

    public JdbcProjectRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public PageSlice<ProjectRecord> findPage(UUID organizationId, String search, String status, UUID clientId,
                                              int page, int size) {
        String where = " WHERE p.organization_id = :organizationId AND LOWER(p.name) LIKE :search";
        if (status != null) where += " AND p.status = :status";
        if (clientId != null) where += " AND p.client_id = :clientId";
        var items = jdbc.sql(SELECT + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset")
                .param("organizationId", organizationId).param("search", "%" + search.toLowerCase() + "%")
                .param("limit", size).param("offset", page * size);
        var count = jdbc.sql("SELECT COUNT(*) FROM projects p" + where)
                .param("organizationId", organizationId).param("search", "%" + search.toLowerCase() + "%");
        if (status != null) { items = items.param("status", status); count = count.param("status", status); }
        if (clientId != null) { items = items.param("clientId", clientId); count = count.param("clientId", clientId); }
        return new PageSlice<>(items.query(this::mapProject).list(), page, size,
                count.query(Long.class).single());
    }

    @Override
    public Optional<ProjectRecord> findById(UUID organizationId, UUID projectId) {
        return jdbc.sql(SELECT + " WHERE p.organization_id = :organizationId AND p.id = :id")
                .param("organizationId", organizationId).param("id", projectId)
                .query(this::mapProject).optional();
    }

    @Override
    public ProjectRecord insert(ProjectRecord project, UUID actorId) {
        jdbc.sql("""
                INSERT INTO projects (id, organization_id, client_id, service_id, name, description, status,
                    value_minor, currency_code, target_start_date, previous_status, archived_at,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :clientId, :serviceId, :name, :description, :status,
                    :valueMinor, :currencyCode, :targetStartDate, NULL, NULL,
                    :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", project.id()).param("organizationId", project.organizationId())
                .param("clientId", project.clientId()).param("serviceId", project.serviceId())
                .param("name", project.name()).param("description", project.description())
                .param("status", project.status().name()).param("valueMinor", project.valueMinor())
                .param("currencyCode", project.currencyCode()).param("targetStartDate", project.targetStartDate())
                .param("createdAt", timestamp(project.createdAt())).param("updatedAt", timestamp(project.updatedAt()))
                .param("actor", actorId).update();
        return project;
    }

    @Override
    public boolean update(UUID organizationId, UUID projectId, ProjectRecord replacement, long version,
                          UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE projects SET client_id = :clientId, service_id = :serviceId, name = :name,
                    description = :description, value_minor = :valueMinor, currency_code = :currencyCode,
                    target_start_date = :targetStartDate, updated_at = :now, updated_by = :actor,
                    version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND status NOT IN ('CANCELLED', 'COMPLETED', 'ARCHIVED')
                """).param("clientId", replacement.clientId()).param("serviceId", replacement.serviceId())
                .param("name", replacement.name()).param("description", replacement.description())
                .param("valueMinor", replacement.valueMinor()).param("currencyCode", replacement.currencyCode())
                .param("targetStartDate", replacement.targetStartDate()).param("now", timestamp(now))
                .param("actor", actorId).param("organizationId", organizationId).param("id", projectId)
                .param("version", version).update() == 1;
    }

    @Override
    public boolean transition(UUID organizationId, UUID projectId, ProjectRecord.Status current,
                              ProjectRecord.Status next, ProjectRecord.Status previousStatus, long version,
                              UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE projects SET status = :next, previous_status = :previousStatus,
                    archived_at = CASE WHEN :next = 'ARCHIVED' THEN :now ELSE archived_at END,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND status = :current AND version = :version
                """).param("next", next.name()).param("previousStatus", previousStatus == null ? null : previousStatus.name())
                .param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", projectId).param("current", current.name()).param("version", version).update() == 1;
    }

    @Override
    public List<ProjectMember> findMembers(UUID organizationId, UUID projectId) {
        return jdbc.sql("""
                SELECT pm.*, ou.user_id, u.display_name, u.email
                FROM project_members pm
                JOIN organization_users ou ON ou.organization_id = pm.organization_id AND ou.id = pm.membership_id
                JOIN users u ON u.id = ou.user_id
                WHERE pm.organization_id = :organizationId AND pm.project_id = :projectId
                ORDER BY u.display_name, pm.id
                """).param("organizationId", organizationId).param("projectId", projectId)
                .query(this::mapMember).list();
    }

    @Override
    public ProjectMember insertMember(ProjectMember member, UUID actorId) {
        jdbc.sql("""
                INSERT INTO project_members (id, organization_id, project_id, membership_id,
                    assignment_role, created_at, created_by)
                SELECT :id, :organizationId, :projectId, ou.id, :assignmentRole, :createdAt, :actor
                FROM organization_users ou WHERE ou.organization_id = :organizationId
                    AND ou.id = :membershipId AND ou.status = 'ACTIVE'
                """).param("id", member.id()).param("organizationId", member.organizationId())
                .param("projectId", member.projectId()).param("membershipId", member.membershipId())
                .param("assignmentRole", member.assignmentRole()).param("createdAt", timestamp(member.createdAt()))
                .param("actor", actorId).update();
        return member;
    }

    @Override
    public boolean removeMember(UUID organizationId, UUID projectId, UUID memberId) {
        return jdbc.sql("""
                DELETE FROM project_members WHERE organization_id = :organizationId
                    AND project_id = :projectId AND id = :id
                """).param("organizationId", organizationId).param("projectId", projectId)
                .param("id", memberId).update() == 1;
    }

    @Override
    public List<ActivityEntry> findActivity(UUID organizationId, UUID projectId, int limit) {
        return jdbc.sql("""
                SELECT * FROM activity_logs WHERE organization_id = :organizationId AND project_id = :projectId
                ORDER BY created_at DESC, id DESC LIMIT :limit
                """).param("organizationId", organizationId).param("projectId", projectId).param("limit", limit)
                .query(this::mapActivity).list();
    }

    @Override
    public void appendActivity(ActivityEntry entry) {
        jdbc.sql("""
                INSERT INTO activity_logs (id, organization_id, project_id, actor_user_id, action,
                    entity_type, entity_id, details, created_at)
                VALUES (:id, :organizationId, :projectId, :actor, :action, :entityType, :entityId, :details, :createdAt)
                """).param("id", entry.id()).param("organizationId", entry.organizationId())
                .param("projectId", entry.projectId()).param("actor", entry.actorUserId())
                .param("action", entry.action()).param("entityType", entry.entityType())
                .param("entityId", entry.entityId()).param("details", entry.details())
                .param("createdAt", timestamp(entry.createdAt())).update();
    }

    private ProjectRecord mapProject(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String previous = rs.getString("previous_status");
        return new ProjectRecord(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getString("client_status"),
                rs.getObject("service_id", UUID.class), rs.getString("service_name"), rs.getString("service_code"),
                rs.getString("name"), rs.getString("description"),
                ProjectRecord.Status.valueOf(rs.getString("status")), (Long) rs.getObject("value_minor"),
                rs.getString("currency_code"), rs.getObject("target_start_date", LocalDate.class),
                previous == null ? null : ProjectRecord.Status.valueOf(previous), instant(rs, "archived_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private ProjectMember mapMember(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ProjectMember(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getObject("membership_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getString("email"),
                rs.getString("assignment_role"), instant(rs, "created_at"));
    }

    private ActivityEntry mapActivity(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ActivityEntry(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getObject("actor_user_id", UUID.class),
                rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                rs.getString("details"), instant(rs, "created_at"));
    }
}
