package com.brainserve.onboarding.onboarding.domain.model;

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
@Table(name = "onboarding_instances", schema = "client_onboarding")
public class OnboardingInstance {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "template_id", nullable = false) private UUID templateId;
    @Column(name = "template_version_id", nullable = false) private UUID templateVersionId;
    @Column(name = "template_name_snapshot", nullable = false, length = 180) private String templateNameSnapshot;
    @Column(name = "template_version_number", nullable = false) private int templateVersionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private OnboardingStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "paused_from_status", length = 40) private OnboardingStatus pausedFromStatus;
    @Column(nullable = false) private boolean ready;
    @Column(name = "start_idempotency_key_hash", nullable = false, length = 64) private String startIdempotencyKeyHash;
    @Column(name = "start_request_hash", nullable = false, length = 64) private String startRequestHash;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_by_type", nullable = false, length = 16) private String updatedByType;
    @Version private long version;

    protected OnboardingInstance() {}

    public OnboardingInstance(UUID id, UUID organizationId, UUID projectId, UUID templateId, UUID templateVersionId,
                              String templateNameSnapshot, int templateVersionNumber, String idempotencyKeyHash,
                              String requestHash, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.templateId = templateId;
        this.templateVersionId = templateVersionId;
        this.templateNameSnapshot = templateNameSnapshot;
        this.templateVersionNumber = templateVersionNumber;
        this.status = OnboardingStatus.DRAFT;
        this.ready = false;
        this.startIdempotencyKeyHash = idempotencyKeyHash;
        this.startRequestHash = requestHash;
        this.startedAt = now;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByType = "INTERNAL";
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getProjectId() { return projectId; }
    public UUID getTemplateId() { return templateId; }
    public UUID getTemplateVersionId() { return templateVersionId; }
    public String getTemplateNameSnapshot() { return templateNameSnapshot; }
    public int getTemplateVersionNumber() { return templateVersionNumber; }
    public OnboardingStatus getStatus() { return status; }
    public OnboardingStatus getPausedFromStatus() { return pausedFromStatus; }
    public boolean isReady() { return ready; }
    public String getStartIdempotencyKeyHash() { return startIdempotencyKeyHash; }
    public String getStartRequestHash() { return startRequestHash; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void updateReadiness(boolean value, UUID actorId, Instant now) {
        updateReadiness(value, false, actorId, "INTERNAL", now);
    }

    public void updateReadiness(boolean value, UUID actorId, String actorType, Instant now) {
        updateReadiness(value, false, actorId, actorType, now);
    }

    /**
     * Updates mathematical readiness while preserving the final-review revision gate. A review-requested
     * non-blocking step can leave mathematical readiness true, but onboarding must not re-enter final review
     * until every step explicitly returned for revision has been completed.
     */
    public void updateReadiness(boolean value, boolean reviewRevisionOutstanding, UUID actorId, String actorType, Instant now) {
        if (ready != value) {
            ready = value;
            touch(actorId, actorType, now);
        }
        if (ready && !reviewRevisionOutstanding && status == OnboardingStatus.IN_PROGRESS) {
            transitionTo(OnboardingStatus.AWAITING_INTERNAL_REVIEW, actorId, actorType, now);
        } else if (!ready && status == OnboardingStatus.AWAITING_INTERNAL_REVIEW) {
            transitionTo(OnboardingStatus.NEEDS_REVISION, actorId, actorType, now);
        }
    }

    public void transitionTo(OnboardingStatus target, UUID actorId, Instant now) { transitionTo(target, actorId, "INTERNAL", now); }

    public void transitionTo(OnboardingStatus target, UUID actorId, String actorType, Instant now) {
        if (!OnboardingStateMachine.canTransition(status, target)) {
            throw new IllegalStateException("Onboarding cannot transition from " + status + " to " + target);
        }
        if (status == OnboardingStatus.PAUSED && target != OnboardingStatus.CANCELLED && target != pausedFromStatus) {
            throw new IllegalStateException("Paused onboarding can only resume to its previous lifecycle state");
        }
        if (target == OnboardingStatus.PAUSED) {
            pausedFromStatus = status;
        } else if (status == OnboardingStatus.PAUSED) {
            pausedFromStatus = null;
        }
        status = target;
        if (target == OnboardingStatus.COMPLETED) completedAt = now;
        touch(actorId, actorType, now);
    }

    private void touch(UUID actorId, String actorType, Instant now) {
        if (actorType == null || !(actorType.equals("INTERNAL") || actorType.equals("CLIENT") || actorType.equals("SYSTEM"))) {
            throw new IllegalArgumentException("Invalid onboarding actor type");
        }
        if (!actorType.equals("SYSTEM") && actorId == null) throw new IllegalArgumentException("User actor id is required");
        updatedAt = now; updatedBy = actorType.equals("SYSTEM") ? null : actorId; updatedByType = actorType;
    }
}
