package com.brainserve.onboarding.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions", schema = "client_onboarding")
public class AuthSession {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "organization_user_id", nullable = false) private UUID organizationUserId;
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64) private String refreshTokenHash;
    @Column(name = "credentials_version", nullable = false) private long credentialsVersion;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoke_reason", length = 80) private String revokeReason;
    @Column(name = "rotated_to_session_id") private UUID rotatedToSessionId;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(name = "user_agent_hash", length = 64) private String userAgentHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;

    protected AuthSession() {}

    public AuthSession(UUID id, UUID organizationId, UUID userId, UUID organizationUserId,
                       String refreshTokenHash, long credentialsVersion, Instant expiresAt,
                       String ipAddress, String userAgentHash, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.organizationUserId = organizationUserId;
        this.refreshTokenHash = refreshTokenHash;
        this.credentialsVersion = credentialsVersion;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.userAgentHash = userAgentHash;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public UUID getOrganizationUserId() { return organizationUserId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public long getCredentialsVersion() { return credentialsVersion; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public UUID getRotatedToSessionId() { return rotatedToSessionId; }
    public long getVersion() { return version; }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(String reason, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }

    public void rotateTo(UUID newSessionId, Instant now) {
        revoke("ROTATED", now);
        rotatedToSessionId = newSessionId;
        lastUsedAt = now;
    }
}
