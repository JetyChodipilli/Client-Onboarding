package com.brainserve.onboarding.client.domain.model;

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
@Table(name = "clients", schema = "client_onboarding")
public class Client {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 180) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ClientStatus status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected Client() {}

    public Client(UUID id, UUID organizationId, String name, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = requireName(name);
        this.status = ClientStatus.PROSPECT;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public ClientStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void update(String name, ClientStatus status, UUID actorId, Instant now) {
        if (this.status == ClientStatus.ARCHIVED) throw new IllegalStateException("Archived clients cannot be modified");
        if (status == ClientStatus.ARCHIVED) throw new IllegalArgumentException("Use the archive operation to archive a client");
        if (status == null || !ClientStateMachine.canTransition(this.status, status)) {
            throw new IllegalStateException("Client cannot transition from " + this.status + " to " + status);
        }
        this.name = requireName(name);
        this.status = status;
        touch(actorId, now);
    }

    public void archive(UUID actorId, Instant now) {
        if (status != ClientStatus.ARCHIVED) {
            status = ClientStatus.ARCHIVED;
            archivedAt = now;
            touch(actorId, now);
        }
    }

    private void touch(UUID actorId, Instant now) {
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Client name is required");
        String normalized = value.trim();
        if (normalized.length() > 180) throw new IllegalArgumentException("Client name is too long");
        return normalized;
    }
}
