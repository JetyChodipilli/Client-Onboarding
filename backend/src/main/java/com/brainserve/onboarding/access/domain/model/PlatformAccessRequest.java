package com.brainserve.onboarding.access.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_access_requests", schema = "client_onboarding")
public class PlatformAccessRequest {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "onboarding_id", nullable = false) private UUID onboardingId;
    @Column(name = "step_instance_id", nullable = false) private UUID stepInstanceId;
    @Column(name = "access_type_id", nullable = false) private UUID accessTypeId;
    @Column(name = "access_type_version_id", nullable = false) private UUID accessTypeVersionId;
    @Column(name = "access_type_code_snapshot", nullable = false, length = 80) private String accessTypeCodeSnapshot;
    @Column(name = "access_type_name_snapshot", nullable = false, length = 180) private String accessTypeNameSnapshot;
    @Column(name = "guide_version_number", nullable = false) private int guideVersionNumber;
    @Column(name = "guide_description_snapshot", length = 2000) private String guideDescriptionSnapshot;
    @Column(name = "instructions_snapshot", nullable = false, columnDefinition = "text") private String instructionsSnapshot;
    @Column(name = "help_url_snapshot", length = 2000) private String helpUrlSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "resources_snapshot", nullable = false, columnDefinition = "jsonb") private JsonNode resourcesSnapshot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 28) private PlatformAccessRequestStatus status;
    @Column(name = "client_account_identifier", length = 320) private String clientAccountIdentifier;
    @Column(name = "client_submission_note", length = 4000) private String clientSubmissionNote;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "verification_started_at") private Instant verificationStartedAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "revision_requested_at") private Instant revisionRequestedAt;
    @Column(name = "waived_at") private Instant waivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Enumerated(EnumType.STRING) @Column(name = "created_by_type", nullable = false, length = 16) private PlatformAccessActorType createdByType;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Enumerated(EnumType.STRING) @Column(name = "updated_by_type", nullable = false, length = 16) private PlatformAccessActorType updatedByType;
    @Version private long version;

    protected PlatformAccessRequest() {}

    public PlatformAccessRequest(UUID id, UUID organizationId, UUID projectId, UUID clientId, UUID onboardingId,
                                 UUID stepInstanceId, PlatformAccessType type, PlatformAccessGuideVersion guide,
                                 PlatformAccessRequestStatus initialStatus, UUID actorId, PlatformAccessActorType actorType,
                                 Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.clientId = clientId;
        this.onboardingId = onboardingId;
        this.stepInstanceId = stepInstanceId;
        this.accessTypeId = type.getId();
        this.accessTypeVersionId = guide.getId();
        this.accessTypeCodeSnapshot = type.getCode();
        this.accessTypeNameSnapshot = type.getName();
        this.guideVersionNumber = guide.getVersionNumber();
        this.guideDescriptionSnapshot = type.getDescription();
        this.instructionsSnapshot = guide.getInstructionsMarkdown();
        this.helpUrlSnapshot = guide.getHelpUrl();
        this.resourcesSnapshot = guide.getResourcesJson();
        this.status = initialStatus;
        this.createdAt = now;
        this.createdBy = actorType == PlatformAccessActorType.SYSTEM ? null : actorId;
        this.createdByType = actorType;
        touch(actorId, actorType, now);
    }

    public void requested(UUID actorId, PlatformAccessActorType actorType, Instant now) {
        transition(PlatformAccessRequestStatus.REQUESTED, actorId, actorType, now);
    }

    public void submit(String accountIdentifier, String note, UUID actorId, Instant now) {
        if (status != PlatformAccessRequestStatus.REQUESTED && status != PlatformAccessRequestStatus.NEEDS_REVISION) {
            throw new IllegalStateException("Platform access can only be submitted when requested or after revision");
        }
        clientAccountIdentifier = PlatformAccessType.optional(accountIdentifier, 320);
        clientSubmissionNote = PlatformAccessType.optional(note, 4000);
        transition(PlatformAccessRequestStatus.CLIENT_SUBMITTED, actorId, PlatformAccessActorType.CLIENT, now);
        submittedAt = now;
        revisionRequestedAt = null;
    }

    public void startVerification(UUID actorId, Instant now) {
        transition(PlatformAccessRequestStatus.UNDER_VERIFICATION, actorId, PlatformAccessActorType.INTERNAL, now);
        verificationStartedAt = now;
    }

    public void verify(UUID actorId, Instant now) {
        transition(PlatformAccessRequestStatus.VERIFIED, actorId, PlatformAccessActorType.INTERNAL, now);
        verifiedAt = now;
    }

    public void requestRevision(UUID actorId, Instant now) {
        transition(PlatformAccessRequestStatus.NEEDS_REVISION, actorId, PlatformAccessActorType.INTERNAL, now);
        revisionRequestedAt = now;
    }

    public void waive(UUID actorId, Instant now) {
        transition(PlatformAccessRequestStatus.WAIVED, actorId, PlatformAccessActorType.INTERNAL, now);
        waivedAt = now;
    }

    private void transition(PlatformAccessRequestStatus target, UUID actorId, PlatformAccessActorType actorType, Instant now) {
        if (!PlatformAccessStateMachine.canTransition(status, target)) {
            throw new IllegalStateException("Platform access cannot transition from " + status + " to " + target);
        }
        status = target;
        touch(actorId, actorType, now);
    }

    private void touch(UUID actorId, PlatformAccessActorType actorType, Instant now) {
        if (actorType != PlatformAccessActorType.SYSTEM && actorId == null) throw new IllegalArgumentException("Platform access actor is required");
        updatedBy = actorType == PlatformAccessActorType.SYSTEM ? null : actorId;
        updatedByType = actorType;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getProjectId() { return projectId; }
    public UUID getClientId() { return clientId; }
    public UUID getOnboardingId() { return onboardingId; }
    public UUID getStepInstanceId() { return stepInstanceId; }
    public UUID getAccessTypeId() { return accessTypeId; }
    public UUID getAccessTypeVersionId() { return accessTypeVersionId; }
    public String getAccessTypeCodeSnapshot() { return accessTypeCodeSnapshot; }
    public String getAccessTypeNameSnapshot() { return accessTypeNameSnapshot; }
    public int getGuideVersionNumber() { return guideVersionNumber; }
    public String getGuideDescriptionSnapshot() { return guideDescriptionSnapshot; }
    public String getInstructionsSnapshot() { return instructionsSnapshot; }
    public String getHelpUrlSnapshot() { return helpUrlSnapshot; }
    public JsonNode getResourcesSnapshot() { return resourcesSnapshot.deepCopy(); }
    public PlatformAccessRequestStatus getStatus() { return status; }
    public String getClientAccountIdentifier() { return clientAccountIdentifier; }
    public String getClientSubmissionNote() { return clientSubmissionNote; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getVerificationStartedAt() { return verificationStartedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getRevisionRequestedAt() { return revisionRequestedAt; }
    public Instant getWaivedAt() { return waivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
