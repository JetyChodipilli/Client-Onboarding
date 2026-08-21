package com.brainserve.onboarding.access.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_access_type_versions", schema = "client_onboarding")
public class PlatformAccessGuideVersion {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "access_type_id", nullable = false) private UUID accessTypeId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PlatformAccessGuideVersionStatus status;
    @Column(name = "change_note", length = 500) private String changeNote;
    @Column(name = "instructions_markdown", nullable = false, columnDefinition = "text") private String instructionsMarkdown;
    @Column(name = "help_url", length = 2000) private String helpUrl;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "resources_json", nullable = false, columnDefinition = "jsonb") private JsonNode resourcesJson;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by") private UUID publishedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version private long version;

    protected PlatformAccessGuideVersion() {}

    public PlatformAccessGuideVersion(UUID id, UUID organizationId, UUID accessTypeId, int versionNumber,
                                      String changeNote, String instructionsMarkdown, String helpUrl, JsonNode resourcesJson,
                                      UUID actorId, Instant now) {
        if (versionNumber <= 0) throw new IllegalArgumentException("Guide version number must be positive");
        this.id = id;
        this.organizationId = organizationId;
        this.accessTypeId = accessTypeId;
        this.versionNumber = versionNumber;
        this.status = PlatformAccessGuideVersionStatus.DRAFT;
        revise(changeNote, instructionsMarkdown, helpUrl, resourcesJson, actorId, now);
        this.createdAt = now;
        this.createdBy = actorId;
    }

    public void revise(String changeNote, String instructionsMarkdown, String helpUrl, JsonNode resourcesJson,
                       UUID actorId, Instant now) {
        requireDraft();
        this.changeNote = PlatformAccessType.optional(changeNote, 500);
        this.instructionsMarkdown = PlatformAccessType.text(instructionsMarkdown, "Access instructions", 20_000);
        this.helpUrl = PlatformAccessType.optional(helpUrl, 2000);
        this.resourcesJson = resourcesJson == null ? null : resourcesJson.deepCopy();
        if (this.resourcesJson == null || !this.resourcesJson.isArray()) throw new IllegalArgumentException("Guide resources must be an array");
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void publish(UUID actorId, Instant now) {
        requireDraft();
        status = PlatformAccessGuideVersionStatus.PUBLISHED;
        publishedAt = now;
        publishedBy = actorId;
        updatedAt = now;
        updatedBy = actorId;
    }

    public void requireDraft() {
        if (status != PlatformAccessGuideVersionStatus.DRAFT) throw new IllegalStateException("Published guide versions are immutable");
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getAccessTypeId() { return accessTypeId; }
    public int getVersionNumber() { return versionNumber; }
    public PlatformAccessGuideVersionStatus getStatus() { return status; }
    public String getChangeNote() { return changeNote; }
    public String getInstructionsMarkdown() { return instructionsMarkdown; }
    public String getHelpUrl() { return helpUrl; }
    public JsonNode getResourcesJson() { return resourcesJson.deepCopy(); }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getPublishedBy() { return publishedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
