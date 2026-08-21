package com.brainserve.onboarding.workflow.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_templates", schema = "client_onboarding")
public class WorkflowTemplate {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 180) private String name;
    @Column(length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private WorkflowTemplateStatus status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected WorkflowTemplate() {}

    public WorkflowTemplate(UUID id, UUID organizationId, String name, String description, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = requireText(name, "Template name", 180);
        this.description = optional(description, 1000);
        this.status = WorkflowTemplateStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public WorkflowTemplateStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void update(String name, String description, UUID actorId, Instant now) {
        if (status == WorkflowTemplateStatus.ARCHIVED) throw new IllegalStateException("Archived templates cannot be modified");
        this.name = requireText(name, "Template name", 180);
        this.description = optional(description, 1000);
        touch(actorId, now);
    }

    public void archive(UUID actorId, Instant now) {
        if (status != WorkflowTemplateStatus.ARCHIVED) {
            status = WorkflowTemplateStatus.ARCHIVED;
            archivedAt = now;
            touch(actorId, now);
        }
    }

    private void touch(UUID actorId, Instant now) { updatedBy = actorId; updatedAt = now; }

    static String requireText(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }

    static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Text is too long");
        return normalized;
    }
}
