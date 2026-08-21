package com.brainserve.onboarding.servicecatalog.domain.model;

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

@Entity
@Table(name = "services", schema = "client_onboarding")
public class ServiceDefinition {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ServiceStatus status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected ServiceDefinition() {}

    public ServiceDefinition(UUID id, UUID organizationId, String code, String name, String description, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.code = normalizeCode(code);
        this.name = requireText(name, "Service name", 160);
        this.description = optional(description, 1000);
        this.status = ServiceStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ServiceStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void update(String name, String description, UUID actorId, Instant now) {
        if (status == ServiceStatus.ARCHIVED) throw new IllegalStateException("Archived services cannot be modified");
        this.name = requireText(name, "Service name", 160);
        this.description = optional(description, 1000);
        touch(actorId, now);
    }

    public void archive(UUID actorId, Instant now) {
        if (status != ServiceStatus.ARCHIVED) {
            this.status = ServiceStatus.ARCHIVED;
            this.archivedAt = now;
            touch(actorId, now);
        }
    }

    public static String normalizeCode(String input) {
        if (input == null) throw new IllegalArgumentException("Service code is required");
        String code = input.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (code.isBlank() || code.length() > 80) throw new IllegalArgumentException("Service code is invalid");
        return code;
    }

    private void touch(UUID actorId, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    private static String requireText(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Description is too long");
        return normalized;
    }
}
