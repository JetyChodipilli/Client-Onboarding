package com.brainserve.onboarding.organization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_invitations", schema = "client_onboarding")
public class OrganizationInvitation {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 320) private String email;
    @Column(name = "normalized_email", nullable = false, length = 320) private String normalizedEmail;
    @Column(name = "display_name", nullable = false, length = 160) private String displayName;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "invited_by", nullable = false) private UUID invitedBy;
    @Column(name = "accepted_by_user_id") private UUID acceptedByUserId;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected OrganizationInvitation() {}

    public OrganizationInvitation(UUID id, UUID organizationId, String email, String normalizedEmail,
                                  String displayName, String tokenHash, Instant expiresAt,
                                  UUID invitedBy, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email.trim();
        this.normalizedEmail = normalizedEmail;
        this.displayName = displayName.trim();
        this.tokenHash = tokenHash;
        this.status = "PENDING";
        this.expiresAt = expiresAt;
        this.invitedBy = invitedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getInvitedBy() { return invitedBy; }
    public long getVersion() { return version; }

    public boolean isUsable(Instant now) { return "PENDING".equals(status) && expiresAt.isAfter(now); }
    public void accept(UUID userId, Instant now) {
        this.status = "ACCEPTED";
        this.acceptedByUserId = userId;
        this.acceptedAt = now;
        this.updatedAt = now;
    }
    public void revoke(Instant now) { this.status = "REVOKED"; this.updatedAt = now; }
}
