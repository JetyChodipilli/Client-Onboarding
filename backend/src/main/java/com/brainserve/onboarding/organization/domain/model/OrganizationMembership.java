package com.brainserve.onboarding.organization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_users", schema = "client_onboarding")
public class OrganizationMembership {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "joined_at") private Instant joinedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Version private long version;

    protected OrganizationMembership() {}

    public OrganizationMembership(UUID id, UUID organizationId, UUID userId, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.status = "ACTIVE";
        this.joinedAt = now;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }

    public void suspend(UUID actorId, Instant now) {
        this.status = "SUSPENDED";
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void activate(UUID actorId, Instant now) {
        this.status = "ACTIVE";
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void markRolesChanged(UUID actorId, Instant now) {
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
}
