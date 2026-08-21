package com.brainserve.onboarding.project.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members", schema = "client_onboarding")
public class ProjectMember {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "organization_user_id", nullable = false) private UUID organizationUserId;
    @Column(length = 120) private String responsibility;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;

    protected ProjectMember() {}

    public ProjectMember(UUID id, UUID organizationId, UUID projectId, UUID organizationUserId,
                         String responsibility, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.organizationUserId = organizationUserId;
        this.responsibility = normalizeResponsibility(responsibility);
        this.createdAt = now;
        this.createdBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getProjectId() { return projectId; }
    public UUID getOrganizationUserId() { return organizationUserId; }
    public String getResponsibility() { return responsibility; }
    public Instant getCreatedAt() { return createdAt; }

    private static String normalizeResponsibility(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 120) throw new IllegalArgumentException("Project responsibility is too long");
        return normalized;
    }
}
