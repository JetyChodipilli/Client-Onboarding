package com.brainserve.clientonboarding.workflow.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateVersion;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowCondition;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowTemplate;
import com.brainserve.clientonboarding.workflow.domain.repository.WorkflowTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowTemplateRepository implements WorkflowTemplateRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcWorkflowTemplateRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    @Override
    public PageSlice<WorkflowTemplate> findPage(UUID organizationId, String search, int page, int size) {
        String match = "%" + search.toLowerCase() + "%";
        var items = jdbc.sql("""
                SELECT * FROM onboarding_templates WHERE organization_id = :organizationId
                    AND LOWER(name) LIKE :search ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset
                """).param("organizationId", organizationId).param("search", match).param("limit", size)
                .param("offset", page * size).query(this::mapTemplate).list();
        long total = jdbc.sql("""
                SELECT COUNT(*) FROM onboarding_templates WHERE organization_id = :organizationId
                    AND LOWER(name) LIKE :search
                """).param("organizationId", organizationId).param("search", match).query(Long.class).single();
        return new PageSlice<>(items, page, size, total);
    }

    @Override
    public Optional<WorkflowTemplate> findTemplate(UUID organizationId, UUID templateId) {
        return jdbc.sql("SELECT * FROM onboarding_templates WHERE organization_id = :organizationId AND id = :id")
                .param("organizationId", organizationId).param("id", templateId)
                .query(this::mapTemplate).optional();
    }

    @Override
    public boolean lockActiveTemplate(UUID organizationId, UUID templateId) {
        return jdbc.sql("""
                SELECT status FROM onboarding_templates
                WHERE organization_id = :organizationId AND id = :templateId FOR UPDATE
                """).param("organizationId", organizationId).param("templateId", templateId)
                .query(String.class).optional().filter("ACTIVE"::equals).isPresent();
    }

    @Override
    public WorkflowTemplate insertTemplate(WorkflowTemplate template, UUID actorId) {
        jdbc.sql("""
                INSERT INTO onboarding_templates (id, organization_id, service_id, name, description, status,
                    archived_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :serviceId, :name, :description, :status, NULL,
                    :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", template.id()).param("organizationId", template.organizationId())
                .param("serviceId", template.serviceId()).param("name", template.name())
                .param("description", template.description()).param("status", template.status().name())
                .param("createdAt", timestamp(template.createdAt())).param("updatedAt", timestamp(template.updatedAt()))
                .param("actor", actorId).update();
        return template;
    }

    @Override
    public boolean archiveTemplate(UUID organizationId, UUID templateId, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE onboarding_templates SET status = 'ARCHIVED', archived_at = :now, updated_at = :now,
                    updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL
                """).param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", templateId).param("version", version).update() == 1;
    }

    @Override
    public List<TemplateVersion> findVersions(UUID organizationId, UUID templateId) {
        return jdbc.sql("""
                SELECT * FROM onboarding_template_versions WHERE organization_id = :organizationId
                    AND template_id = :templateId ORDER BY version_number DESC
                """).param("organizationId", organizationId).param("templateId", templateId)
                .query(this::mapVersion).list();
    }

    @Override
    public Optional<TemplateVersion> findVersion(UUID organizationId, UUID versionId) {
        return jdbc.sql("""
                SELECT * FROM onboarding_template_versions WHERE organization_id = :organizationId AND id = :id
                """).param("organizationId", organizationId).param("id", versionId)
                .query(this::mapVersion).optional();
    }

    @Override
    public int nextVersionNumber(UUID organizationId, UUID templateId) {
        String status = jdbc.sql("""
                SELECT status FROM onboarding_templates
                WHERE organization_id = :organizationId AND id = :templateId FOR UPDATE
                """).param("organizationId", organizationId).param("templateId", templateId)
                .query(String.class).single();
        if (!"ACTIVE".equals(status)) return 0;
        return jdbc.sql("""
                SELECT COALESCE(MAX(version_number), 0) + 1 FROM onboarding_template_versions
                WHERE organization_id = :organizationId AND template_id = :templateId
                """).param("organizationId", organizationId).param("templateId", templateId)
                .query(Integer.class).single();
    }

    @Override
    public TemplateVersion insertVersion(TemplateVersion version, UUID actorId) {
        jdbc.sql("""
                INSERT INTO onboarding_template_versions (id, organization_id, template_id, version_number,
                    status, published_at, published_by, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :templateId, :number, :status, NULL, NULL,
                    :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", version.id()).param("organizationId", version.organizationId())
                .param("templateId", version.templateId()).param("number", version.versionNumber())
                .param("status", version.status().name()).param("createdAt", timestamp(version.createdAt()))
                .param("updatedAt", timestamp(version.updatedAt())).param("actor", actorId).update();
        return version;
    }

    @Override
    public List<TemplateStep> findSteps(UUID organizationId, UUID versionId) {
        List<StepRow> rows = jdbc.sql("""
                SELECT * FROM onboarding_template_steps WHERE organization_id = :organizationId
                    AND template_version_id = :versionId ORDER BY display_order, id
                """).param("organizationId", organizationId).param("versionId", versionId)
                .query(this::mapStepRow).list();
        Map<UUID, List<UUID>> dependencies = new HashMap<>();
        jdbc.sql("""
                SELECT step_id, depends_on_step_id FROM onboarding_step_dependencies
                WHERE organization_id = :organizationId AND template_version_id = :versionId
                """).param("organizationId", organizationId).param("versionId", versionId)
                .query((rs, rowNum) -> new UUID[] { rs.getObject(1, UUID.class), rs.getObject(2, UUID.class) })
                .list().forEach(pair -> dependencies.computeIfAbsent(pair[0], ignored -> new ArrayList<>()).add(pair[1]));
        return rows.stream().map(row -> row.toStep(dependencies.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public boolean replaceDraftSteps(UUID organizationId, UUID versionId, long expectedVersion,
                                     List<TemplateStep> steps, UUID actorId, Instant now) {
        VersionLock lock = jdbc.sql("""
                SELECT status, version FROM onboarding_template_versions
                WHERE organization_id = :organizationId AND id = :versionId FOR UPDATE
                """).param("organizationId", organizationId).param("versionId", versionId)
                .query((rs, rowNum) -> new VersionLock(rs.getString("status"), rs.getLong("version"))).single();
        if (!"DRAFT".equals(lock.status())) return false;
        if (lock.version() != expectedVersion) return false;
        jdbc.sql("DELETE FROM onboarding_step_dependencies WHERE organization_id = :organizationId AND template_version_id = :versionId")
                .param("organizationId", organizationId).param("versionId", versionId).update();
        jdbc.sql("DELETE FROM onboarding_template_steps WHERE organization_id = :organizationId AND template_version_id = :versionId")
                .param("organizationId", organizationId).param("versionId", versionId).update();
        for (TemplateStep step : steps) {
            jdbc.sql("""
                    INSERT INTO onboarding_template_steps (id, organization_id, template_version_id, step_key,
                        name, description, step_type, display_order, required, blocking, client_visible,
                        requires_review, dependency_mode, condition_expression, assigned_role, due_after_hours,
                        reminder_policy_id, allow_skip, allow_reopen, configuration_json,
                        created_at, created_by, updated_at, updated_by, version)
                    VALUES (:id, :organizationId, :versionId, :stepKey, :name, :description, :stepType,
                        :displayOrder, :required, :blocking, :clientVisible, :requiresReview, :dependencyMode,
                        :condition, :assignedRole, :dueAfterHours, :reminderPolicyId, :allowSkip, :allowReopen,
                        :configuration, :now, :actor, :now, :actor, 0)
                    """).param("id", step.id()).param("organizationId", organizationId).param("versionId", versionId)
                    .param("stepKey", step.stepKey()).param("name", step.name()).param("description", step.description())
                    .param("stepType", step.stepType().name()).param("displayOrder", step.displayOrder())
                    .param("required", step.required()).param("blocking", step.blocking())
                    .param("clientVisible", step.clientVisible()).param("requiresReview", step.requiresReview())
                    .param("dependencyMode", step.dependencyMode().name()).param("condition", condition(step.condition()))
                    .param("assignedRole", step.assignedRole()).param("dueAfterHours", step.dueAfterHours())
                    .param("reminderPolicyId", step.reminderPolicyId()).param("allowSkip", step.allowSkip())
                    .param("allowReopen", step.allowReopen()).param("configuration", write(step.configuration()))
                    .param("now", timestamp(now)).param("actor", actorId).update();
        }
        for (TemplateStep step : steps) {
            for (UUID dependency : step.dependencyStepIds()) {
                jdbc.sql("""
                        INSERT INTO onboarding_step_dependencies (organization_id, template_version_id,
                            step_id, depends_on_step_id, created_at, created_by)
                        VALUES (:organizationId, :versionId, :stepId, :dependencyId, :now, :actor)
                        """).param("organizationId", organizationId).param("versionId", versionId)
                        .param("stepId", step.id()).param("dependencyId", dependency)
                        .param("now", timestamp(now)).param("actor", actorId).update();
            }
        }
        jdbc.sql("""
                UPDATE onboarding_template_versions SET updated_at = :now, updated_by = :actor,
                    version = version + 1 WHERE organization_id = :organizationId AND id = :versionId
                    AND version = :expectedVersion
                """).param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("versionId", versionId).param("expectedVersion", expectedVersion).update();
        return true;
    }

    @Override
    public boolean publishVersion(UUID organizationId, UUID versionId, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE onboarding_template_versions SET status = 'PUBLISHED', published_at = :now,
                    published_by = :actor, updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version AND status = 'DRAFT'
                """).param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", versionId).param("version", version).update() == 1;
    }

    private WorkflowTemplate mapTemplate(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkflowTemplate(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("service_id", UUID.class), rs.getString("name"), rs.getString("description"),
                WorkflowTemplate.Status.valueOf(rs.getString("status")), instant(rs, "archived_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private TemplateVersion mapVersion(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TemplateVersion(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("template_id", UUID.class), rs.getInt("version_number"),
                TemplateVersion.Status.valueOf(rs.getString("status")), instant(rs, "published_at"),
                rs.getObject("published_by", UUID.class), instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private StepRow mapStepRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new StepRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("template_version_id", UUID.class), rs.getString("step_key"), rs.getString("name"),
                rs.getString("description"), TemplateStep.StepType.valueOf(rs.getString("step_type")),
                rs.getInt("display_order"), rs.getBoolean("required"), rs.getBoolean("blocking"),
                rs.getBoolean("client_visible"), rs.getBoolean("requires_review"),
                TemplateStep.DependencyMode.valueOf(rs.getString("dependency_mode")),
                readCondition(rs.getString("condition_expression")), rs.getString("assigned_role"),
                (Integer) rs.getObject("due_after_hours"), rs.getObject("reminder_policy_id", UUID.class),
                rs.getBoolean("allow_skip"), rs.getBoolean("allow_reopen"),
                readMap(rs.getString("configuration_json")), instant(rs, "created_at"),
                instant(rs, "updated_at"), rs.getLong("version"));
    }

    private String condition(WorkflowCondition value) { return value == null ? "" : write(value); }
    private WorkflowCondition readCondition(String value) {
        if (value == null || value.isBlank()) return null;
        try { return json.readValue(value, WorkflowCondition.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored workflow condition", exception); }
    }
    private Map<String, Object> readMap(String value) {
        try { return json.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored workflow configuration", exception); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Workflow configuration is not JSON serializable", exception); }
    }

    private record StepRow(UUID id, UUID organizationId, UUID versionId, String stepKey, String name,
                           String description, TemplateStep.StepType stepType, int displayOrder, boolean required,
                           boolean blocking, boolean clientVisible, boolean requiresReview,
                           TemplateStep.DependencyMode dependencyMode, WorkflowCondition condition,
                           String assignedRole, Integer dueAfterHours, UUID reminderPolicyId,
                           boolean allowSkip, boolean allowReopen, Map<String, Object> configuration,
                           Instant createdAt, Instant updatedAt, long version) {
        private TemplateStep toStep(List<UUID> dependencies) {
            return new TemplateStep(id, organizationId, versionId, stepKey, name, description, stepType,
                    displayOrder, required, blocking, clientVisible, requiresReview, dependencyMode, condition,
                    assignedRole, dueAfterHours, reminderPolicyId, allowSkip, allowReopen, configuration,
                    dependencies, createdAt, updatedAt, version);
        }
    }

    private record VersionLock(String status, long version) { }
}
