package com.brainserve.clientonboarding.assets.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssetModels {
    private AssetModels() { }
    public enum Status { REQUESTED, UPLOADED, SCANNING, SUBMITTED, UNDER_REVIEW, APPROVED, NEEDS_REVISION, REPLACED, QUARANTINED, REJECTED }
    public enum ScanStatus { PENDING, SCANNING, CLEAN, INFECTED, ERROR }
    public enum Decision { UNDER_REVIEW, APPROVED, NEEDS_REVISION, REOPENED, SKIPPED }
    public record Requirement(UUID id, UUID organizationId, String name, String instructions, List<String> allowedMimes,
                              long maxBytes, Instant archivedAt, Instant createdAt, long version) { }
    public record Asset(UUID id, UUID organizationId, UUID stepId, UUID requirementId, UUID currentVersionId, long version) { }
    public record FileVersion(UUID id, UUID organizationId, UUID assetId, int versionNumber, String filename,
                              String declaredMime, String detectedMime, long byteSize, String sha256, String objectKey,
                              String objectVersionId, Status status, ScanStatus scanStatus, String scanMessage,
                              String reviewNote, Instant uploadExpiresAt, UUID scanLeaseId, Instant scanLeaseUntil,
                              Instant scannedAt, Instant createdAt, long version) {
        public FileVersion change(String objectVersion, Status next, ScanStatus scan, String detected,
                                  String message, String note, UUID lease, Instant until, Instant scanned) {
            return new FileVersion(id, organizationId, assetId, versionNumber, filename, declaredMime, detected,
                    byteSize, sha256, objectKey, objectVersion, next, scan, message, note, uploadExpiresAt,
                    lease, until, scanned, createdAt, version + 1);
        }
        public FileVersion status(Status next, String note) {
            return change(objectVersionId, next, scanStatus, detectedMime, scanMessage, note, null, null, scannedAt);
        }
    }
    public record History(FileVersion file, List<Review> reviews) { }
    public record Review(Decision decision, String note, Instant createdAt, UUID createdBy) { }
}
