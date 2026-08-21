package com.brainserve.onboarding.access.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_access_reviews", schema = "client_onboarding")
public class PlatformAccessReview {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "access_request_id", nullable = false) private UUID accessRequestId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 28) private PlatformAccessReviewAction action;
    @Column(length = 2000) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;

    protected PlatformAccessReview() {}

    public PlatformAccessReview(UUID id, UUID organizationId, UUID requestId, UUID projectId,
                                PlatformAccessReviewAction action, String reason, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.accessRequestId = requestId;
        this.projectId = projectId;
        this.action = action;
        this.reason = PlatformAccessType.optional(reason, 2000);
        if ((action == PlatformAccessReviewAction.REQUEST_REVISION || action == PlatformAccessReviewAction.WAIVE)
                && this.reason == null) throw new IllegalArgumentException("A reason is required for this review action");
        this.createdAt = now;
        this.createdBy = actorId;
    }

    public UUID getId() { return id; }
    public PlatformAccessReviewAction getAction() { return action; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
}
