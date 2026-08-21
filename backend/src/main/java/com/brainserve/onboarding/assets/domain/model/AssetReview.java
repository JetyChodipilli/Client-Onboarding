package com.brainserve.onboarding.assets.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_reviews", schema = "client_onboarding")
public class AssetReview {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "asset_id", nullable = false) private UUID assetId;
    @Column(name = "asset_version_id", nullable = false) private UUID assetVersionId;
    @Column(name = "reviewer_user_id", nullable = false) private UUID reviewerUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AssetReviewAction action;
    @Column(length = 2000) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AssetReview() {}

    public AssetReview(UUID id, UUID organizationId, UUID assetId, UUID assetVersionId, UUID reviewerUserId,
                       AssetReviewAction action, String note, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.assetId = assetId;
        this.assetVersionId = assetVersionId;
        this.reviewerUserId = reviewerUserId;
        this.action = action;
        this.note = note == null || note.isBlank() ? null : note.trim();
        if (this.note != null && this.note.length() > 2000) throw new IllegalArgumentException("Review note is too long");
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAssetId() { return assetId; }
    public UUID getAssetVersionId() { return assetVersionId; }
    public UUID getReviewerUserId() { return reviewerUserId; }
    public AssetReviewAction getAction() { return action; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
