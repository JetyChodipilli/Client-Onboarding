package com.brainserve.onboarding.assets.api.response;

import com.brainserve.onboarding.assets.domain.model.AssetVersionStatus;
import java.time.Instant;
import java.util.UUID;

public record AssetVersionResponse(
        UUID id,
        int versionNumber,
        String filename,
        long expectedSizeBytes,
        Long actualSizeBytes,
        String declaredMimeType,
        String detectedMimeType,
        String sha256,
        AssetVersionStatus status,
        Instant uploadedAt,
        Instant scannedAt,
        int scanAttemptCount,
        String rejectionReason,
        Instant createdAt,
        long version) {}
