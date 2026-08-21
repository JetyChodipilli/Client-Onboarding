package com.brainserve.onboarding.access.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "platform_access_types", schema = "client_onboarding")
public class PlatformAccessType {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 180) private String name;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PlatformAccessTypeStatus status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected PlatformAccessType() {}

    public PlatformAccessType(UUID id, UUID organizationId, String code, String name, String description,
                              UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.code = normalizeCode(code);
        this.name = text(name, "Access type name", 180);
        this.description = optional(description, 2000);
        this.status = PlatformAccessTypeStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void update(String name, String description, UUID actorId, Instant now) {
        if (status != PlatformAccessTypeStatus.ACTIVE) throw new IllegalStateException("Archived access types cannot be edited");
        this.name = text(name, "Access type name", 180);
        this.description = optional(description, 2000);
        touch(actorId, now);
    }

    public void archive(UUID actorId, Instant now) {
        if (status == PlatformAccessTypeStatus.ARCHIVED) return;
        status = PlatformAccessTypeStatus.ARCHIVED;
        archivedAt = now;
        touch(actorId, now);
    }

    private void touch(UUID actorId, Instant now) { updatedBy = actorId; updatedAt = now; }

    public static String normalizeCode(String input) {
        if (input == null) throw new IllegalArgumentException("Access type code is required");
        String value = input.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (value.length() < 2 || value.length() > 80 || !value.matches("[A-Z0-9][A-Z0-9_]*")) {
            throw new IllegalArgumentException("Access type code is invalid");
        }
        return value;
    }

    static String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }

    static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Value is too long");
        return normalized;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public PlatformAccessTypeStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
