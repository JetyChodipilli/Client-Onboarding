package com.brainserve.onboarding.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "client_onboarding")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 320, unique = true)
    private String normalizedEmail;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "credentials_version", nullable = false)
    private long credentialsVersion;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_secret_encrypted")
    private String mfaSecretEncrypted;

    @Column(name = "mfa_pending_secret_encrypted")
    private String mfaPendingSecretEncrypted;

    @Column(name = "mfa_pending_created_at")
    private Instant mfaPendingCreatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(UUID id, String email, String displayName, String passwordHash, Instant now, boolean verified) {
        this.id = id;
        this.email = email.trim();
        this.normalizedEmail = normalizeEmail(email);
        this.displayName = displayName.trim();
        this.passwordHash = passwordHash;
        this.status = "ACTIVE";
        this.emailVerifiedAt = verified ? now : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getStatus() { return status; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public long getCredentialsVersion() { return credentialsVersion; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public String getMfaSecretEncrypted() { return mfaSecretEncrypted; }
    public String getMfaPendingSecretEncrypted() { return mfaPendingSecretEncrypted; }
    public Instant getMfaPendingCreatedAt() { return mfaPendingCreatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public long getVersion() { return version; }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailedLogin(Instant now, int maxAttempts, java.time.Duration lockDuration) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxAttempts) {
            this.lockedUntil = now.plus(lockDuration);
            this.failedLoginCount = 0;
        }
        this.updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public void markEmailVerified(Instant now) {
        this.emailVerifiedAt = now;
        this.updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.credentialsVersion++;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void stageMfaSecret(String encryptedSecret, Instant now) {
        this.mfaPendingSecretEncrypted = encryptedSecret;
        this.mfaPendingCreatedAt = now;
        this.updatedAt = now;
    }

    public void enableMfaFromPending(Instant now) {
        if (mfaPendingSecretEncrypted == null) {
            throw new IllegalStateException("No MFA enrollment is pending");
        }
        this.mfaSecretEncrypted = this.mfaPendingSecretEncrypted;
        this.mfaPendingSecretEncrypted = null;
        this.mfaPendingCreatedAt = null;
        this.mfaEnabled = true;
        this.credentialsVersion++;
        this.updatedAt = now;
    }
}
