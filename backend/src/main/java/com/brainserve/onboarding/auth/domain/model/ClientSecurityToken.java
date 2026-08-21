package com.brainserve.onboarding.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_security_tokens", schema = "client_onboarding")
public class ClientSecurityToken {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_type", nullable = false, length = 40) private String tokenType;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;

    protected ClientSecurityToken() {}

    public ClientSecurityToken(UUID id, UUID organizationId, UUID userId, String tokenType,
                               String tokenHash, Instant expiresAt, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.tokenType = tokenType;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public String getTokenType() { return tokenType; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public long getVersion() { return version; }

    public boolean usable(Instant now) {
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
    public void consume(Instant now) { if (!usable(now)) throw new IllegalStateException("Security token is not usable"); consumedAt = now; }
    public void revoke(Instant now) { if (consumedAt == null) revokedAt = now; }
}
