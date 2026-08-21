package com.brainserve.onboarding.assets.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "asset_requirements", schema = "client_onboarding")
public class AssetRequirement {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "onboarding_id", nullable = false) private UUID onboardingId;
    @Column(name = "step_instance_id", nullable = false) private UUID stepInstanceId;
    @Column(name = "form_submission_id") private UUID formSubmissionId;
    @Column(name = "form_field_id") private UUID formFieldId;
    @Column(name = "requirement_key", nullable = false, length = 120) private String requirementKey;
    @Column(nullable = false, length = 180) private String name;
    @Column(length = 2000) private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "allowed_mime_types", nullable = false, columnDefinition = "jsonb") private JsonNode allowedMimeTypes;
    @Column(name = "max_file_size_bytes", nullable = false) private long maxFileSizeBytes;
    @Column(nullable = false) private boolean required;
    @Column(name = "requires_review", nullable = false) private boolean requiresReview;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected AssetRequirement() {}

    public AssetRequirement(UUID id, UUID organizationId, UUID projectId, UUID onboardingId, UUID stepInstanceId,
                            UUID formSubmissionId, UUID formFieldId, String requirementKey, String name, String description,
                            JsonNode allowedMimeTypes, long maxFileSizeBytes, boolean required, boolean requiresReview,
                            UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.onboardingId = onboardingId;
        this.stepInstanceId = stepInstanceId;
        this.formSubmissionId = formSubmissionId;
        this.formFieldId = formFieldId;
        this.requirementKey = text(requirementKey, "Requirement key", 120);
        this.name = text(name, "Asset name", 180);
        this.description = optional(description, 2000);
        this.allowedMimeTypes = allowedMimeTypes.deepCopy();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.required = required;
        this.requiresReview = requiresReview;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getProjectId() { return projectId; }
    public UUID getOnboardingId() { return onboardingId; }
    public UUID getStepInstanceId() { return stepInstanceId; }
    public UUID getFormSubmissionId() { return formSubmissionId; }
    public UUID getFormFieldId() { return formFieldId; }
    public String getRequirementKey() { return requirementKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public JsonNode getAllowedMimeTypes() { return allowedMimeTypes.deepCopy(); }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public boolean isRequired() { return required; }
    public boolean isRequiresReview() { return requiresReview; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Text is too long");
        return normalized;
    }
}
