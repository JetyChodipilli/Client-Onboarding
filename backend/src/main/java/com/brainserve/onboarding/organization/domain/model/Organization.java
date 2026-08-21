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
@Table(name = "organizations", schema = "client_onboarding")
public class Organization {
    @Id private UUID id;
    @Column(nullable = false, length = 180) private String name;
    @Column(nullable = false, length = 80) private String slug;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Version private long version;

    protected Organization() {}

    public Organization(UUID id, String name, String slug, UUID actorId, Instant now) {
        this.id = id;
        this.name = requireText(name, "Organization name");
        this.slug = normalizeSlug(slug);
        this.status = "ACTIVE";
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }

    public void updateName(String name, UUID actorId, Instant now) {
        this.name = requireText(name, "Organization name");
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public static String normalizeSlug(String value) {
        String slug = requireText(value, "Organization slug").trim().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9](?:[a-z0-9-]{1,78}[a-z0-9])?")) {
            throw new IllegalArgumentException("Organization slug must contain lowercase letters, numbers, or hyphens");
        }
        return slug;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
