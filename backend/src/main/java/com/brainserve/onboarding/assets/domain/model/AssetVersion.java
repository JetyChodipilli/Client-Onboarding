package com.brainserve.onboarding.assets.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "asset_versions", schema = "client_onboarding")
public class AssetVersion {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "asset_id", nullable = false) private UUID assetId;
    @Column(name = "requirement_id", nullable = false) private UUID requirementId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "sanitized_filename", nullable = false, length = 180) private String sanitizedFilename;
    @Column(name = "storage_bucket", nullable = false, length = 160) private String storageBucket;
    @Column(name = "object_key", nullable = false, length = 600) private String objectKey;
    @Column(name = "expected_size_bytes", nullable = false) private long expectedSizeBytes;
    @Column(name = "actual_size_bytes") private Long actualSizeBytes;
    @Column(name = "declared_mime_type", nullable = false, length = 160) private String declaredMimeType;
    @Column(name = "detected_mime_type", length = 160) private String detectedMimeType;
    @Column(name = "expected_sha256", length = 64) private String expectedSha256;
    @Column(name = "actual_sha256", length = 64) private String actualSha256;
    @Column(name = "object_etag", length = 180) private String objectEtag;
    @Column(name = "upload_idempotency_key", nullable = false, length = 128) private String uploadIdempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AssetVersionStatus status;
    @Column(name = "upload_expires_at", nullable = false) private Instant uploadExpiresAt;
    @Column(name = "uploaded_at") private Instant uploadedAt;
    @Column(name = "scan_started_at") private Instant scanStartedAt;
    @Column(name = "scanned_at") private Instant scannedAt;
    @Column(name = "scan_attempt_count", nullable = false) private int scanAttemptCount;
    @Column(name = "next_scan_at") private Instant nextScanAt;
    @Column(name = "malware_signature", length = 500) private String malwareSignature;
    @Column(name = "rejection_reason", length = 1000) private String rejectionReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected AssetVersion() {}

    public AssetVersion(UUID id, UUID organizationId, UUID assetId, UUID requirementId, UUID projectId,
                        int versionNumber, String originalFilename, String sanitizedFilename, String storageBucket,
                        String objectKey, long expectedSizeBytes, String declaredMimeType, String expectedSha256,
                        String uploadIdempotencyKey, Instant uploadExpiresAt, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.assetId = assetId;
        this.requirementId = requirementId;
        this.projectId = projectId;
        this.versionNumber = versionNumber;
        this.originalFilename = originalFilename;
        this.sanitizedFilename = sanitizedFilename;
        this.storageBucket = storageBucket;
        this.objectKey = objectKey;
        this.expectedSizeBytes = expectedSizeBytes;
        this.declaredMimeType = normalizeMime(declaredMimeType);
        this.expectedSha256 = normalizeSha(expectedSha256);
        this.uploadIdempotencyKey = uploadIdempotencyKey;
        this.status = AssetVersionStatus.PENDING_UPLOAD;
        this.uploadExpiresAt = uploadExpiresAt;
        this.createdAt = now;
        this.createdBy = actorId;
        this.updatedAt = now;
    }

    public boolean uploadExpired(Instant now) { return status == AssetVersionStatus.PENDING_UPLOAD && !now.isBefore(uploadExpiresAt); }

    public void uploaded(long actualSize, String etag, Instant now) {
        require(AssetVersionStatus.PENDING_UPLOAD);
        if (uploadExpired(now)) throw new IllegalStateException("Upload authorization has expired");
        this.actualSizeBytes = actualSize;
        this.objectEtag = bounded(etag, 180);
        this.uploadedAt = now;
        this.status = AssetVersionStatus.UPLOADED;
        this.updatedAt = now;
    }

    public void queueScan(Instant now) {
        if (status != AssetVersionStatus.UPLOADED && status != AssetVersionStatus.SCAN_FAILED) {
            throw new IllegalStateException("Asset version cannot be queued for scanning from " + status);
        }
        status = AssetVersionStatus.SCAN_PENDING;
        nextScanAt = now;
        rejectionReason = null;
        updatedAt = now;
    }

    public void scanStarted(Instant now) {
        if (status != AssetVersionStatus.SCAN_PENDING && status != AssetVersionStatus.SCAN_FAILED) {
            throw new IllegalStateException("Asset version is not waiting for a malware scan");
        }
        status = AssetVersionStatus.SCANNING;
        scanStartedAt = now;
        scanAttemptCount++;
        nextScanAt = null;
        updatedAt = now;
    }

    public void clean(String detectedMimeType, String actualSha256, Instant now) {
        require(AssetVersionStatus.SCANNING);
        this.detectedMimeType = normalizeMime(detectedMimeType);
        this.actualSha256 = normalizeSha(actualSha256);
        this.status = AssetVersionStatus.CLEAN;
        this.scannedAt = now;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    public void quarantined(String signature, String detectedMimeType, String actualSha256, Instant now) {
        require(AssetVersionStatus.SCANNING);
        this.detectedMimeType = detectedMimeType == null ? null : normalizeMime(detectedMimeType);
        this.actualSha256 = actualSha256 == null ? null : normalizeSha(actualSha256);
        this.malwareSignature = bounded(signature, 500);
        this.status = AssetVersionStatus.QUARANTINED;
        this.scannedAt = now;
        this.rejectionReason = "Malware detected";
        this.updatedAt = now;
    }

    public void rejected(String reason, String detectedMimeType, String actualSha256, Instant now) {
        if (status != AssetVersionStatus.SCANNING && status != AssetVersionStatus.PENDING_UPLOAD && status != AssetVersionStatus.UPLOADED) {
            throw new IllegalStateException("Asset version cannot be rejected from " + status);
        }
        boolean rejectedDuringScan = status == AssetVersionStatus.SCANNING;
        this.detectedMimeType = detectedMimeType == null ? this.detectedMimeType : normalizeMime(detectedMimeType);
        this.actualSha256 = actualSha256 == null ? this.actualSha256 : normalizeSha(actualSha256);
        this.status = AssetVersionStatus.REJECTED;
        this.scannedAt = rejectedDuringScan ? now : this.scannedAt;
        this.rejectionReason = bounded(reason, 1000);
        this.updatedAt = now;
    }

    public void scanFailed(String reason, Duration delay, Instant now) {
        require(AssetVersionStatus.SCANNING);
        this.status = AssetVersionStatus.SCAN_FAILED;
        this.rejectionReason = bounded(reason, 1000);
        this.nextScanAt = now.plus(delay);
        this.updatedAt = now;
    }

    public void resetScan(Instant now) {
        if (status != AssetVersionStatus.SCAN_FAILED) throw new IllegalStateException("Only failed scans can be retried");
        this.scanAttemptCount = 0;
        this.rejectionReason = null;
        this.status = AssetVersionStatus.SCAN_PENDING;
        this.nextScanAt = now;
        this.updatedAt = now;
    }

    private void require(AssetVersionStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private static String normalizeMime(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MIME type is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 160) throw new IllegalArgumentException("MIME type is too long");
        return normalized;
    }

    private static String normalizeSha(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 must be a 64-character lowercase hexadecimal digest");
        return normalized;
    }

    private static String bounded(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getAssetId() { return assetId; }
    public UUID getRequirementId() { return requirementId; }
    public UUID getProjectId() { return projectId; }
    public int getVersionNumber() { return versionNumber; }
    public String getOriginalFilename() { return originalFilename; }
    public String getSanitizedFilename() { return sanitizedFilename; }
    public String getStorageBucket() { return storageBucket; }
    public String getObjectKey() { return objectKey; }
    public long getExpectedSizeBytes() { return expectedSizeBytes; }
    public Long getActualSizeBytes() { return actualSizeBytes; }
    public String getDeclaredMimeType() { return declaredMimeType; }
    public String getDetectedMimeType() { return detectedMimeType; }
    public String getExpectedSha256() { return expectedSha256; }
    public String getActualSha256() { return actualSha256; }
    public String getObjectEtag() { return objectEtag; }
    public String getUploadIdempotencyKey() { return uploadIdempotencyKey; }
    public AssetVersionStatus getStatus() { return status; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getScanStartedAt() { return scanStartedAt; }
    public Instant getScannedAt() { return scannedAt; }
    public int getScanAttemptCount() { return scanAttemptCount; }
    public Instant getNextScanAt() { return nextScanAt; }
    public String getMalwareSignature() { return malwareSignature; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
