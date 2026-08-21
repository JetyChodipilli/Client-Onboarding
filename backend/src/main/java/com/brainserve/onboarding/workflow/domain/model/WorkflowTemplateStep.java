package com.brainserve.onboarding.workflow.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "onboarding_template_steps", schema = "client_onboarding")
public class WorkflowTemplateStep {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "template_version_id", nullable = false) private UUID templateVersionId;
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
    @Column(name = "due_after_hours") private Integer dueAfterHours;
    @Column(name = "reminder_policy_id") private UUID reminderPolicyId;
    @Column(name = "allow_skip", nullable = false) private boolean allowSkip;
    @Column(name = "allow_reopen", nullable = false) private boolean allowReopen;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "configuration_json", nullable = false, columnDefinition = "jsonb") private JsonNode configurationJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected WorkflowTemplateStep() {}

    public WorkflowTemplateStep(UUID id, UUID organizationId, UUID templateVersionId, String stepKey, String name,
                                String description, WorkflowStepType stepType, int displayOrder, boolean required,
                                boolean blocking, boolean clientVisible, boolean requiresReview, DependencyMode dependencyMode,
                                JsonNode conditionExpression, UUID assignedRoleId, Integer dueAfterHours, UUID reminderPolicyId,
                                boolean allowSkip, boolean allowReopen, JsonNode configurationJson, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.templateVersionId = templateVersionId;
        this.stepKey = normalizeStepKey(stepKey);
        this.name = WorkflowTemplate.requireText(name, "Step name", 180);
        this.description = WorkflowTemplate.optional(description, 2000);
        this.stepType = stepType;
        if (displayOrder < 0) throw new IllegalArgumentException("Display order cannot be negative");
        this.displayOrder = displayOrder;
        this.required = required;
        this.blocking = blocking;
        this.clientVisible = clientVisible;
        this.requiresReview = requiresReview;
        this.dependencyMode = dependencyMode;
        this.conditionExpression = conditionExpression.deepCopy();
        this.assignedRoleId = assignedRoleId;
        if (dueAfterHours != null && (dueAfterHours < 0 || dueAfterHours > 8760)) throw new IllegalArgumentException("Due hours must be between 0 and 8760");
        this.dueAfterHours = dueAfterHours;
        this.reminderPolicyId = reminderPolicyId;
        this.allowSkip = allowSkip;
        this.allowReopen = allowReopen;
        this.configurationJson = configurationJson.deepCopy();
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getTemplateVersionId() { return templateVersionId; }
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
    public Integer getDueAfterHours() { return dueAfterHours; }
    public UUID getReminderPolicyId() { return reminderPolicyId; }
    public boolean isAllowSkip() { return allowSkip; }
    public boolean isAllowReopen() { return allowReopen; }
    public JsonNode getConfigurationJson() { return configurationJson.deepCopy(); }
    public long getVersion() { return version; }

    public static String normalizeStepKey(String input) {
        if (input == null) throw new IllegalArgumentException("Step key is required");
        String value = input.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (value.isBlank() || value.length() > 80) throw new IllegalArgumentException("Step key is invalid");
        return value;
    }
}
