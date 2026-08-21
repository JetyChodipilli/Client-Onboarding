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
@Table(name = "onboarding_template_versions", schema = "client_onboarding")
public class WorkflowTemplateVersion {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "template_id", nullable = false) private UUID templateId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private WorkflowVersionStatus status;
    @Column(name = "change_note", length = 500) private String changeNote;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by") private UUID publishedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected WorkflowTemplateVersion() {}

    public WorkflowTemplateVersion(UUID id, UUID organizationId, UUID templateId, int versionNumber,
                                   String changeNote, UUID actorId, Instant now) {
        if (versionNumber < 1) throw new IllegalArgumentException("Version number must be positive");
        this.id = id;
        this.organizationId = organizationId;
        this.templateId = templateId;
        this.versionNumber = versionNumber;
        this.status = WorkflowVersionStatus.DRAFT;
        this.changeNote = WorkflowTemplate.optional(changeNote, 500);
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getTemplateId() { return templateId; }
    public int getVersionNumber() { return versionNumber; }
    public WorkflowVersionStatus getStatus() { return status; }
    public String getChangeNote() { return changeNote; }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getPublishedBy() { return publishedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void revise(String changeNote, UUID actorId, Instant now) {
        requireDraft();
        this.changeNote = WorkflowTemplate.optional(changeNote, 500);
        touch(actorId, now);
    }

    public void touchDraft(UUID actorId, Instant now) {
        requireDraft();
        touch(actorId, now);
    }

    public void publish(UUID actorId, Instant now) {
        requireDraft();
        status = WorkflowVersionStatus.PUBLISHED;
        publishedAt = now;
        publishedBy = actorId;
        touch(actorId, now);
    }

    public void requireDraft() {
        if (status != WorkflowVersionStatus.DRAFT) throw new IllegalStateException("Published workflow versions are immutable");
    }

    private void touch(UUID actorId, Instant now) { updatedBy = actorId; updatedAt = now; }
}
