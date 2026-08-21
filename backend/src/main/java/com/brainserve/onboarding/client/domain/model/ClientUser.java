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
@Table(name = "client_users", schema = "client_onboarding")
public class ClientUser {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ClientUserStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Version private long version;

    protected ClientUser() {}

    public ClientUser(UUID id, UUID organizationId, UUID clientId, UUID userId, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.clientId = clientId;
        this.userId = userId;
        this.status = ClientUserStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientId() { return clientId; }
    public UUID getUserId() { return userId; }
    public ClientUserStatus getStatus() { return status; }
    public long getVersion() { return version; }

    public void reactivate(UUID actorId, Instant now) {
        if (status == ClientUserStatus.SUSPENDED) {
            throw new IllegalStateException("Suspended client accounts require an internal administrator to restore access");
        }
        status = ClientUserStatus.ACTIVE;
        updatedBy = actorId;
        updatedAt = now;
    }
}
