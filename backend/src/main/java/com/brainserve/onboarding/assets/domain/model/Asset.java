package com.brainserve.onboarding.assets.domain.model;

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
@Table(name = "assets", schema = "client_onboarding")
public class Asset {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "requirement_id", nullable = false) private UUID requirementId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "onboarding_id", nullable = false) private UUID onboardingId;
    @Column(name = "step_instance_id", nullable = false) private UUID stepInstanceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AssetStatus status;
    @Column(name = "current_version_id") private UUID currentVersionId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Enumerated(EnumType.STRING) @Column(name = "updated_by_type", nullable = false, length = 16) private AssetActorType updatedByType;
    @Version private long version;

    protected Asset() {}

    public Asset(UUID id, UUID organizationId, UUID requirementId, UUID projectId, UUID onboardingId,
                 UUID stepInstanceId, UUID actorId, AssetActorType actorType, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.requirementId = requirementId;
        this.projectId = projectId;
        this.onboardingId = onboardingId;
        this.stepInstanceId = stepInstanceId;
        this.status = AssetStatus.REQUESTED;
        this.createdAt = now;
        this.createdBy = actorId;
        touch(actorId, actorType, now);
    }

    public void uploaded(UUID versionId, UUID actorId, AssetActorType actorType, Instant now) {
        AssetStatus target = switch (status) {
            case REQUESTED -> AssetStatus.UPLOADED;
            case NEEDS_REVISION, QUARANTINED, REJECTED -> AssetStatus.REPLACED;
            default -> throw new IllegalStateException("Asset cannot accept a replacement from " + status);
        };
        transition(target, actorId, actorType, now);
        currentVersionId = versionId;
    }

    public void scanStarted(Instant now) { transition(AssetStatus.SCANNING, null, AssetActorType.SYSTEM, now); }

    public void clean(UUID actorId, AssetActorType actorType, Instant now) {
        transition(AssetStatus.SUBMITTED, actorId, actorType, now);
    }

    public void startReview(UUID actorId, Instant now) { transition(AssetStatus.UNDER_REVIEW, actorId, AssetActorType.INTERNAL, now); }
    public void approve(UUID actorId, AssetActorType actorType, Instant now) { transition(AssetStatus.APPROVED, actorId, actorType, now); }
    public void requestRevision(UUID actorId, Instant now) { transition(AssetStatus.NEEDS_REVISION, actorId, AssetActorType.INTERNAL, now); }
    public void quarantine(Instant now) { transition(AssetStatus.QUARANTINED, null, AssetActorType.SYSTEM, now); }
    public void reject(UUID actorId, AssetActorType actorType, Instant now) { transition(AssetStatus.REJECTED, actorId, actorType, now); }

    private void transition(AssetStatus target, UUID actorId, AssetActorType actorType, Instant now) {
        if (!AssetStateMachine.canTransition(status, target)) {
            throw new IllegalStateException("Asset cannot transition from " + status + " to " + target);
        }
        status = target;
        touch(actorId, actorType, now);
    }

    private void touch(UUID actorId, AssetActorType actorType, Instant now) {
        updatedAt = now;
        updatedByType = actorType;
        updatedBy = actorType == AssetActorType.SYSTEM ? null : actorId;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getRequirementId() { return requirementId; }
    public UUID getProjectId() { return projectId; }
    public UUID getOnboardingId() { return onboardingId; }
    public UUID getStepInstanceId() { return stepInstanceId; }
    public AssetStatus getStatus() { return status; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
