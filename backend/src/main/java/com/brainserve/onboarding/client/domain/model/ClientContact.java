package com.brainserve.onboarding.client.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "client_contacts", schema = "client_onboarding")
public class ClientContact {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "display_name", nullable = false, length = 160) private String displayName;
    @Column(nullable = false, length = 320) private String email;
    @Column(name = "normalized_email", nullable = false, length = 320) private String normalizedEmail;
    @Column(name = "job_title", length = 120) private String jobTitle;
    @Column(length = 40) private String phone;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected ClientContact() {}

    public ClientContact(UUID id, UUID organizationId, UUID clientId, String displayName, String email,
                         String jobTitle, String phone, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.clientId = clientId;
        apply(displayName, email, jobTitle, phone);
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientId() { return clientId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getJobTitle() { return jobTitle; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void update(String displayName, String email, String jobTitle, String phone, UUID actorId, Instant now) {
        apply(displayName, email, jobTitle, phone);
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void apply(String displayName, String email, String jobTitle, String phone) {
        this.displayName = requireText(displayName, "Contact name", 160);
        this.email = requireText(email, "Contact email", 320);
        this.normalizedEmail = normalizeEmail(email);
        this.jobTitle = optional(jobTitle, 120);
        this.phone = optional(phone, 40);
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
        if (normalized.length() > max) throw new IllegalArgumentException("Value is too long");
        return normalized;
    }
}
