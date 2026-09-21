package com.brainserve.clientonboarding.onboarding.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.onboarding.domain.repository.OnboardingRepository;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowCondition;
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
public class JdbcOnboardingRepository implements OnboardingRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcOnboardingRepository(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override
    public Optional<OnboardingInstance> findById(UUID organizationId, UUID onboardingId) {
        return jdbc.sql("SELECT * FROM onboarding_instances WHERE organization_id = :organizationId AND id = :id")
                .param("organizationId", organizationId).param("id", onboardingId)
                .query(this::mapOnboarding).optional();
    }

    @Override
    public Optional<OnboardingInstance> findByProject(UUID organizationId, UUID projectId) {
        return jdbc.sql("""
                SELECT * FROM onboarding_instances WHERE organization_id = :organizationId AND project_id = :projectId
                """).param("organizationId", organizationId).param("projectId", projectId)
                .query(this::mapOnboarding).optional();
    }

    @Override
    public void lockTenantCommands(UUID organizationId) {
        jdbc.sql("SELECT id FROM organizations WHERE id = :organizationId FOR UPDATE")
                .param("organizationId", organizationId)
                .query((resultSet, rowNumber) -> Boolean.TRUE)
                .single();
    }

    @Override
    public Optional<IdempotentCommand> findIdempotentCommand(UUID organizationId, String scope, String key) {
        return jdbc.sql("""
                SELECT resource_id, request_fingerprint FROM command_idempotency
                WHERE organization_id = :organizationId AND command_scope = :scope AND idempotency_key = :key
                """).param("organizationId", organizationId).param("scope", scope).param("key", key)
                .query((rs, rowNum) -> new IdempotentCommand(rs.getObject("resource_id", UUID.class),
                        rs.getString("request_fingerprint"))).optional();
    }

    @Override
    public void insertIdempotency(UUID organizationId, String scope, String key, UUID resourceId,
                                  String fingerprint, Instant now) {
        jdbc.sql("""
                INSERT INTO command_idempotency (organization_id, command_scope, idempotency_key,
                    resource_id, request_fingerprint, created_at)
                VALUES (:organizationId, :scope, :key, :resourceId, :fingerprint, :now)
                """).param("organizationId", organizationId).param("scope", scope).param("key", key)
                .param("resourceId", resourceId).param("fingerprint", fingerprint)
                .param("now", timestamp(now)).update();
    }

    @Override
    public OnboardingInstance insert(OnboardingInstance instance, List<OnboardingStepInstance> steps,
                                     UUID actorId) {
        jdbc.sql("""
                INSERT INTO onboarding_instances (id, organization_id, project_id, source_template_id,
                    source_template_version_id, snapshot_version_number, snapshot_json, status, ready,
                    started_at, completed_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :projectId, :templateId, :templateVersionId, :snapshotNumber,
                    :snapshot, :status, :ready, :startedAt, NULL, :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", instance.id()).param("organizationId", instance.organizationId())
                .param("projectId", instance.projectId()).param("templateId", instance.sourceTemplateId())
                .param("templateVersionId", instance.sourceTemplateVersionId())
                .param("snapshotNumber", instance.snapshotVersionNumber()).param("snapshot", instance.snapshotJson())
                .param("status", instance.status().name()).param("ready", instance.ready())
                .param("startedAt", timestamp(instance.startedAt())).param("createdAt", timestamp(instance.createdAt()))
                .param("updatedAt", timestamp(instance.updatedAt())).param("actor", actorId).update();
        for (OnboardingStepInstance step : steps) insertStep(step, actorId);
        for (OnboardingStepInstance step : steps) {
            for (UUID dependencyId : step.dependencyStepInstanceIds()) {
                jdbc.sql("""
                        INSERT INTO onboarding_step_instance_dependencies (organization_id, onboarding_id,
                            step_instance_id, depends_on_step_instance_id)
                        VALUES (:organizationId, :onboardingId, :stepId, :dependencyId)
                        """).param("organizationId", instance.organizationId()).param("onboardingId", instance.id())
                        .param("stepId", step.id()).param("dependencyId", dependencyId).update();
            }
        }
        return instance;
    }

    private void insertStep(OnboardingStepInstance step, UUID actorId) {
        jdbc.sql("""
                INSERT INTO onboarding_step_instances (id, organization_id, onboarding_id, source_step_id,
                    step_key, name, description, step_type, display_order, required, blocking, client_visible,
                    requires_review, dependency_mode, condition_expression, assigned_role, due_at,
                    allow_skip, allow_reopen, configuration_json, applicable, status, completed_at,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :onboardingId, :sourceStepId, :stepKey, :name, :description,
                    :stepType, :displayOrder, :required, :blocking, :clientVisible, :requiresReview,
                    :dependencyMode, :condition, :assignedRole, :dueAt, :allowSkip, :allowReopen,
                    :configuration, :applicable, :status, NULL, :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", step.id()).param("organizationId", step.organizationId())
                .param("onboardingId", step.onboardingId()).param("sourceStepId", step.sourceStepId())
                .param("stepKey", step.stepKey()).param("name", step.name()).param("description", step.description())
                .param("stepType", step.stepType().name()).param("displayOrder", step.displayOrder())
                .param("required", step.required()).param("blocking", step.blocking())
                .param("clientVisible", step.clientVisible()).param("requiresReview", step.requiresReview())
                .param("dependencyMode", step.dependencyMode().name())
                .param("condition", step.condition() == null ? "" : write(step.condition()))
                .param("assignedRole", step.assignedRole()).param("dueAt", timestamp(step.dueAt()))
                .param("allowSkip", step.allowSkip()).param("allowReopen", step.allowReopen())
                .param("configuration", write(step.configuration())).param("applicable", step.applicable())
                .param("status", step.status().name()).param("createdAt", timestamp(step.createdAt()))
                .param("updatedAt", timestamp(step.updatedAt())).param("actor", actorId).update();
    }

    @Override
    public List<OnboardingStepInstance> findSteps(UUID organizationId, UUID onboardingId) {
        List<StepRow> rows = jdbc.sql("""
                SELECT * FROM onboarding_step_instances WHERE organization_id = :organizationId
                    AND onboarding_id = :onboardingId ORDER BY display_order, id
                """).param("organizationId", organizationId).param("onboardingId", onboardingId)
                .query(this::mapStepRow).list();
        Map<UUID, List<UUID>> dependencies = new HashMap<>();
        jdbc.sql("""
                SELECT step_instance_id, depends_on_step_instance_id FROM onboarding_step_instance_dependencies
                WHERE organization_id = :organizationId AND onboarding_id = :onboardingId
                """).param("organizationId", organizationId).param("onboardingId", onboardingId)
                .query((rs, rowNum) -> new UUID[] { rs.getObject(1, UUID.class), rs.getObject(2, UUID.class) })
                .list().forEach(pair -> dependencies.computeIfAbsent(pair[0], ignored -> new ArrayList<>()).add(pair[1]));
        return rows.stream().map(row -> row.toStep(dependencies.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public Optional<OnboardingStepInstance> findStep(UUID organizationId, UUID stepId) {
        return jdbc.sql("SELECT onboarding_id FROM onboarding_step_instances WHERE organization_id = :organizationId AND id = :id")
                .param("organizationId", organizationId).param("id", stepId).query(UUID.class).optional()
                .flatMap(onboardingId -> findSteps(organizationId, onboardingId).stream()
                        .filter(step -> step.id().equals(stepId)).findFirst());
    }

    @Override
    public boolean updateStepStatus(UUID organizationId, UUID stepId, OnboardingStepInstance.Status current,
                                    OnboardingStepInstance.Status next, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE onboarding_step_instances SET status = :next,
                    completed_at = CASE WHEN :next = 'COMPLETED' THEN :now ELSE NULL END,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND status = :current AND version = :version
                """).param("next", next.name()).param("now", timestamp(now)).param("actor", actorId)
                .param("organizationId", organizationId).param("id", stepId).param("current", current.name())
                .param("version", version).update() == 1;
    }

    @Override
    public void refreshAvailability(UUID organizationId, UUID onboardingId, UUID actorId, Instant now) {
        jdbc.sql("""
                UPDATE onboarding_step_instances AS target SET status = 'AVAILABLE', updated_at = :now,
                    updated_by = :actor, version = version + 1
                WHERE target.organization_id = :organizationId AND target.onboarding_id = :onboardingId
                    AND target.applicable = TRUE AND target.status = 'LOCKED' AND (
                        target.dependency_mode = 'NONE'
                        OR (target.dependency_mode = 'ALL' AND NOT EXISTS (
                            SELECT 1 FROM onboarding_step_instance_dependencies d
                            JOIN onboarding_step_instances dependency
                              ON dependency.organization_id = d.organization_id
                             AND dependency.id = d.depends_on_step_instance_id
                            WHERE d.organization_id = target.organization_id
                              AND d.step_instance_id = target.id AND dependency.status <> 'COMPLETED'))
                        OR (target.dependency_mode = 'ANY' AND EXISTS (
                            SELECT 1 FROM onboarding_step_instance_dependencies d
                            JOIN onboarding_step_instances dependency
                              ON dependency.organization_id = d.organization_id
                             AND dependency.id = d.depends_on_step_instance_id
                            WHERE d.organization_id = target.organization_id
                              AND d.step_instance_id = target.id AND dependency.status = 'COMPLETED'))
                    )
                """).param("now", timestamp(now)).param("actor", actorId)
                .param("organizationId", organizationId).param("onboardingId", onboardingId).update();
    }

    @Override
    public boolean updateReadiness(UUID organizationId, UUID onboardingId, boolean ready, long version,
                                   UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE onboarding_instances SET ready = :ready, updated_at = :now, updated_by = :actor,
                    version = version + 1 WHERE organization_id = :organizationId AND id = :id AND version = :version
                """).param("ready", ready).param("now", timestamp(now)).param("actor", actorId)
                .param("organizationId", organizationId).param("id", onboardingId).param("version", version)
                .update() == 1;
    }

    private OnboardingInstance mapOnboarding(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OnboardingInstance(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getObject("source_template_id", UUID.class),
                rs.getObject("source_template_version_id", UUID.class), rs.getInt("snapshot_version_number"),
                rs.getString("snapshot_json"), OnboardingInstance.Status.valueOf(rs.getString("status")),
                rs.getBoolean("ready"), instant(rs, "started_at"), instant(rs, "completed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private StepRow mapStepRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String conditionValue = rs.getString("condition_expression");
        WorkflowCondition condition = conditionValue == null || conditionValue.isBlank() ? null
                : read(conditionValue, WorkflowCondition.class);
        return new StepRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("onboarding_id", UUID.class), rs.getObject("source_step_id", UUID.class),
                rs.getString("step_key"), rs.getString("name"), rs.getString("description"),
                TemplateStep.StepType.valueOf(rs.getString("step_type")), rs.getInt("display_order"),
                rs.getBoolean("required"), rs.getBoolean("blocking"), rs.getBoolean("client_visible"),
                rs.getBoolean("requires_review"), TemplateStep.DependencyMode.valueOf(rs.getString("dependency_mode")),
                condition, rs.getString("assigned_role"), instant(rs, "due_at"), rs.getBoolean("allow_skip"),
                rs.getBoolean("allow_reopen"), readMap(rs.getString("configuration_json")),
                rs.getBoolean("applicable"), OnboardingStepInstance.Status.valueOf(rs.getString("status")),
                instant(rs, "completed_at"), instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private Map<String, Object> readMap(String value) {
        try { return json.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored step configuration", exception); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored workflow data", exception); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Workflow data is not JSON serializable", exception); }
    }

    private record StepRow(UUID id, UUID organizationId, UUID onboardingId, UUID sourceStepId, String stepKey,
                           String name, String description, TemplateStep.StepType stepType, int displayOrder,
                           boolean required, boolean blocking, boolean clientVisible, boolean requiresReview,
                           TemplateStep.DependencyMode dependencyMode, WorkflowCondition condition,
                           String assignedRole, Instant dueAt, boolean allowSkip, boolean allowReopen,
                           Map<String, Object> configuration, boolean applicable,
                           OnboardingStepInstance.Status status, Instant completedAt, Instant createdAt,
                           Instant updatedAt, long version) {
        private OnboardingStepInstance toStep(List<UUID> dependencies) {
            return new OnboardingStepInstance(id, organizationId, onboardingId, sourceStepId, stepKey, name,
                    description, stepType, displayOrder, required, blocking, clientVisible, requiresReview,
                    dependencyMode, condition, assignedRole, dueAt, allowSkip, allowReopen, configuration,
                    applicable, status, dependencies, completedAt, createdAt, updatedAt, version);
        }
    }
}
