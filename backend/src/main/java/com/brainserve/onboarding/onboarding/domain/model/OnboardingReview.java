package com.brainserve.onboarding.onboarding.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "onboarding_reviews", schema = "client_onboarding")
public class OnboardingReview {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "onboarding_id", nullable = false) private UUID onboardingId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private OnboardingReviewAction action;
    @Column(name = "reviewer_user_id", nullable = false) private UUID reviewerUserId;
    @Column(length = 2000) private String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "revision_step_ids", nullable = false, columnDefinition = "jsonb") private JsonNode revisionStepIds;
    @Column(name = "onboarding_version", nullable = false) private long onboardingVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected OnboardingReview() {}

    public OnboardingReview(UUID id, UUID organizationId, UUID onboardingId, UUID projectId,
                            OnboardingReviewAction action, UUID reviewerUserId, String reason,
                            JsonNode revisionStepIds, long onboardingVersion, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.onboardingId = onboardingId;
        this.projectId = projectId;
        this.action = action;
        this.reviewerUserId = reviewerUserId;
        this.reason = normalize(reason);
        this.revisionStepIds = revisionStepIds.deepCopy();
        this.onboardingVersion = onboardingVersion;
        this.createdAt = createdAt;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 2000) throw new IllegalArgumentException("Review reason is too long");
        return normalized;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getOnboardingId() { return onboardingId; }
    public UUID getProjectId() { return projectId; }
    public OnboardingReviewAction getAction() { return action; }
    public UUID getReviewerUserId() { return reviewerUserId; }
    public String getReason() { return reason; }
    public JsonNode getRevisionStepIds() { return revisionStepIds.deepCopy(); }
    public long getOnboardingVersion() { return onboardingVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
