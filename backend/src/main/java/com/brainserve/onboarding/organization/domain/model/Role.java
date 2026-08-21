package com.brainserve.onboarding.organization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "roles", schema = "client_onboarding")
public class Role {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 240) private String description;
    @Column(name = "system_role", nullable = false) private boolean systemRole;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Version private long version;

    protected Role() {}

    public Role(UUID id, UUID organizationId, String code, String name, String description,
                boolean systemRole, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.code = normalizeCode(code);
        this.name = name.trim();
        this.description = description == null ? null : description.trim();
        this.systemRole = systemRole;
        this.status = "ACTIVE";
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
    public boolean isSystemRole() { return systemRole; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }

    public void update(String name, String description, UUID actorId, Instant now) {
        this.name = name.trim();
        this.description = description == null ? null : description.trim();
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public static String normalizeCode(String input) {
        String code = input.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (code.isBlank() || code.length() > 80) throw new IllegalArgumentException("Role code is invalid");
        return code;
    }
}
