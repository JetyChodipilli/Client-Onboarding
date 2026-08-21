package com.brainserve.onboarding.onboarding.domain.model;

import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "onboarding_step_instances", schema = "client_onboarding")
public class OnboardingStepInstance {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "onboarding_id", nullable = false) private UUID onboardingId;
    @Column(name = "template_version_id", nullable = false) private UUID templateVersionId;
    @Column(name = "source_template_step_id", nullable = false) private UUID sourceTemplateStepId;
    @Column(name = "step_key", nullable = false, length = 80) private String stepKey;
    @Column(nullable = false, length = 180) private String name;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "step_type", nullable = false, length = 32) private WorkflowStepType stepType;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean required;
    @Column(nullable = false) private boolean blocking;
    @Column(name = "client_visible", nullable = false) private boolean clientVisible;
    @Column(name = "requires_review", nullable = false) private boolean requiresReview;
    @Enumerated(EnumType.STRING) @Column(name = "dependency_mode", nullable = false, length = 8) private DependencyMode dependencyMode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "condition_expression", nullable = false, columnDefinition = "jsonb") private JsonNode conditionExpression;
    @Column(name = "assigned_role_id") private UUID assignedRoleId;
    @Column(name = "due_at") private Instant dueAt;
    @Column(name = "reminder_policy_id") private UUID reminderPolicyId;
    @Column(name = "allow_skip", nullable = false) private boolean allowSkip;
    @Column(name = "allow_reopen", nullable = false) private boolean allowReopen;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "configuration_json", nullable = false, columnDefinition = "jsonb") private JsonNode configurationJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private OnboardingStepStatus status;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_by_type", nullable = false, length = 16) private String updatedByType;
    @Version private long version;

    protected OnboardingStepInstance() {}

    public OnboardingStepInstance(UUID id, UUID organizationId, UUID onboardingId, UUID templateVersionId, UUID sourceTemplateStepId,
                                  String stepKey, String name, String description, WorkflowStepType stepType, int displayOrder,
                                  boolean required, boolean blocking, boolean clientVisible, boolean requiresReview,
                                  DependencyMode dependencyMode, JsonNode conditionExpression, UUID assignedRoleId,
                                  Instant dueAt, UUID reminderPolicyId, boolean allowSkip, boolean allowReopen,
                                  JsonNode configurationJson, OnboardingStepStatus status, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.onboardingId = onboardingId;
        this.templateVersionId = templateVersionId;
        this.sourceTemplateStepId = sourceTemplateStepId;
        this.stepKey = stepKey;
        this.name = name;
        this.description = description;
        this.stepType = stepType;
        this.displayOrder = displayOrder;
        this.required = required;
        this.blocking = blocking;
        this.clientVisible = clientVisible;
        this.requiresReview = requiresReview;
        this.dependencyMode = dependencyMode;
        this.conditionExpression = conditionExpression.deepCopy();
        this.assignedRoleId = assignedRoleId;
        this.dueAt = dueAt;
        this.reminderPolicyId = reminderPolicyId;
        this.allowSkip = allowSkip;
        this.allowReopen = allowReopen;
        this.configurationJson = configurationJson.deepCopy();
        this.status = status;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByType = "INTERNAL";
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getOnboardingId() { return onboardingId; }
    public UUID getTemplateVersionId() { return templateVersionId; }
    public UUID getSourceTemplateStepId() { return sourceTemplateStepId; }
    public String getStepKey() { return stepKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public WorkflowStepType getStepType() { return stepType; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isRequired() { return required; }
    public boolean isBlocking() { return blocking; }
    public boolean isClientVisible() { return clientVisible; }
    public boolean isRequiresReview() { return requiresReview; }
    public DependencyMode getDependencyMode() { return dependencyMode; }
    public JsonNode getConditionExpression() { return conditionExpression.deepCopy(); }
    public UUID getAssignedRoleId() { return assignedRoleId; }
    public Instant getDueAt() { return dueAt; }
    public UUID getReminderPolicyId() { return reminderPolicyId; }
    public boolean isAllowSkip() { return allowSkip; }
    public boolean isAllowReopen() { return allowReopen; }
    public JsonNode getConfigurationJson() { return configurationJson.deepCopy(); }
    public OnboardingStepStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void transitionTo(OnboardingStepStatus target, UUID actorId, Instant now) { transitionTo(target, actorId, "INTERNAL", now); }

    public void transitionTo(OnboardingStepStatus target, UUID actorId, String actorType, Instant now) {
        if (!OnboardingStepStateMachine.canTransition(status, target, requiresReview, allowSkip, allowReopen)) {
            throw new IllegalStateException("Step cannot transition from " + status + " to " + target);
        }
        status = target;
        completedAt = target == OnboardingStepStatus.COMPLETED ? now : null;
        if (actorType == null || !(actorType.equals("INTERNAL") || actorType.equals("CLIENT") || actorType.equals("SYSTEM"))) {
            throw new IllegalArgumentException("Invalid workflow actor type");
        }
        if (!actorType.equals("SYSTEM") && actorId == null) throw new IllegalArgumentException("User actor id is required");
        updatedAt = now;
        updatedBy = actorType.equals("SYSTEM") ? null : actorId;
        updatedByType = actorType;
    }
}
