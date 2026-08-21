package com.brainserve.onboarding.project.domain.model;

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
@Table(name = "projects", schema = "client_onboarding")
public class Project {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "service_id", nullable = false) private UUID serviceId;
    @Column(nullable = false, length = 180) private String name;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ProjectStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "hold_from_status", length = 24) private ProjectStatus holdFromStatus;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected Project() {}

    public Project(UUID id, UUID organizationId, UUID clientId, UUID serviceId, String name,
                   String description, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.clientId = clientId;
        this.serviceId = serviceId;
        this.name = requireName(name);
        this.description = optionalDescription(description);
        this.status = ProjectStatus.DRAFT;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientId() { return clientId; }
    public UUID getServiceId() { return serviceId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
    public ProjectStatus getHoldFromStatus() { return holdFromStatus; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void update(UUID clientId, UUID serviceId, String name, String description, UUID actorId, Instant now) {
        if (status == ProjectStatus.ARCHIVED || status == ProjectStatus.CANCELLED || status == ProjectStatus.COMPLETED) {
            throw new IllegalStateException("This project can no longer be edited");
        }
        if (status != ProjectStatus.DRAFT && (!this.clientId.equals(clientId) || !this.serviceId.equals(serviceId))) {
            throw new IllegalStateException("Client and service cannot change after onboarding has started");
        }
        this.clientId = clientId;
        this.serviceId = serviceId;
        this.name = requireName(name);
        this.description = optionalDescription(description);
        touch(actorId, now);
    }

    public void cancel(UUID actorId, Instant now) {
        if (!ProjectStateMachine.canCancel(status)) throw new IllegalStateException("Project cannot be cancelled from its current state");
        status = ProjectStatus.CANCELLED;
        holdFromStatus = null;
        touch(actorId, now);
    }

    public void archive(UUID actorId, Instant now) {
        if (!ProjectStateMachine.canArchive(status)) throw new IllegalStateException("Only completed or cancelled projects can be archived");
        status = ProjectStatus.ARCHIVED;
        holdFromStatus = null;
        archivedAt = now;
        touch(actorId, now);
    }

    /** Reserved for the onboarding module in Phase 3+. */
    public void transitionToOnboarding(UUID actorId, Instant now) {
        if (status != ProjectStatus.DRAFT) throw new IllegalStateException("Only draft projects can enter onboarding");
        status = ProjectStatus.ONBOARDING;
        touch(actorId, now);
    }

    /** Reserved for the readiness/final-review module in Phase 11. */
    public void transitionToReady(UUID actorId, Instant now) {
        if (status != ProjectStatus.ONBOARDING) throw new IllegalStateException("Only onboarding projects can become ready");
        status = ProjectStatus.READY;
        touch(actorId, now);
    }

    /** Reserved for authorized project activation in Phase 11. */
    public void activate(UUID actorId, Instant now) {
        if (status != ProjectStatus.READY) throw new IllegalStateException("Only ready projects can be activated");
        status = ProjectStatus.ACTIVE;
        touch(actorId, now);
    }

    public void hold(UUID actorId, Instant now) {
        if (!ProjectStateMachine.canHold(status)) throw new IllegalStateException("Project cannot be put on hold from its current state");
        holdFromStatus = status;
        status = ProjectStatus.ON_HOLD;
        touch(actorId, now);
    }

    public void resume(UUID actorId, Instant now) {
        if (status != ProjectStatus.ON_HOLD || !ProjectStateMachine.canResume(holdFromStatus)) {
            throw new IllegalStateException("Project cannot be resumed to its previous state");
        }
        status = holdFromStatus;
        holdFromStatus = null;
        touch(actorId, now);
    }

    private void touch(UUID actorId, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Project name is required");
        String normalized = value.trim();
        if (normalized.length() > 180) throw new IllegalArgumentException("Project name is too long");
        return normalized;
    }

    private static String optionalDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 2000) throw new IllegalArgumentException("Project description is too long");
        return normalized;
    }
}
