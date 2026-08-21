package com.brainserve.onboarding.assets.domain.model;

public enum AssetVersionStatus {
    PENDING_UPLOAD,
    UPLOADED,
    SCAN_PENDING,
    SCANNING,
    CLEAN,
    SCAN_FAILED,
    QUARANTINED,
    REJECTED
}
