package com.brainserve.onboarding.assets.domain.model;

/** Central validator for the PRD asset lifecycle. Security recovery paths always create a new file version. */
public final class AssetStateMachine {
    private AssetStateMachine() {}

    public static boolean canTransition(AssetStatus from, AssetStatus to) {
        if (from == to) return false;
        return switch (from) {
            case REQUESTED -> to == AssetStatus.UPLOADED || to == AssetStatus.REPLACED;
            case REPLACED, UPLOADED -> to == AssetStatus.SCANNING;
            case SCANNING -> to == AssetStatus.SUBMITTED || to == AssetStatus.QUARANTINED || to == AssetStatus.REJECTED;
            case SUBMITTED -> to == AssetStatus.UNDER_REVIEW || to == AssetStatus.APPROVED;
            case UNDER_REVIEW -> to == AssetStatus.APPROVED || to == AssetStatus.NEEDS_REVISION || to == AssetStatus.REJECTED;
            case NEEDS_REVISION, QUARANTINED, REJECTED -> to == AssetStatus.REPLACED;
            case APPROVED -> to == AssetStatus.NEEDS_REVISION;
        };
    }
}
