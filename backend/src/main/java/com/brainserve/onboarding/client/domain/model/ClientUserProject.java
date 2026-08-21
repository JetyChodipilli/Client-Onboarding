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
@Table(name = "client_user_projects", schema = "client_onboarding")
public class ClientUserProject {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "client_user_id", nullable = false) private UUID clientUserId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(name = "access_level", nullable = false, length = 24) private ClientProjectAccessLevel accessLevel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ClientUserProjectStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Version private long version;

    protected ClientUserProject() {}

    public ClientUserProject(UUID id, UUID organizationId, UUID clientId, UUID clientUserId, UUID projectId,
                             ClientProjectAccessLevel accessLevel, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.clientId = clientId;
        this.clientUserId = clientUserId;
        this.projectId = projectId;
        this.accessLevel = accessLevel;
        this.status = ClientUserProjectStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientId() { return clientId; }
    public UUID getClientUserId() { return clientUserId; }
    public UUID getProjectId() { return projectId; }
    public ClientProjectAccessLevel getAccessLevel() { return accessLevel; }
    public ClientUserProjectStatus getStatus() { return status; }
    public long getVersion() { return version; }

    public void restore(ClientProjectAccessLevel level, UUID actorId, Instant now) {
        accessLevel = level;
        status = ClientUserProjectStatus.ACTIVE;
        updatedBy = actorId;
        updatedAt = now;
    }
}
