package com.brainserve.onboarding.access.domain.model;

/** Server-authoritative state machine for third-party platform access verification. */
public final class PlatformAccessStateMachine {
    private PlatformAccessStateMachine() {}

    public static boolean canTransition(PlatformAccessRequestStatus from, PlatformAccessRequestStatus to) {
        if (from == to) return false;
        return switch (from) {
            case NOT_STARTED -> to == PlatformAccessRequestStatus.REQUESTED || to == PlatformAccessRequestStatus.WAIVED;
            case REQUESTED -> to == PlatformAccessRequestStatus.CLIENT_SUBMITTED || to == PlatformAccessRequestStatus.WAIVED;
            case CLIENT_SUBMITTED -> to == PlatformAccessRequestStatus.UNDER_VERIFICATION;
            case UNDER_VERIFICATION -> to == PlatformAccessRequestStatus.VERIFIED
                    || to == PlatformAccessRequestStatus.NEEDS_REVISION;
            case NEEDS_REVISION -> to == PlatformAccessRequestStatus.CLIENT_SUBMITTED
                    || to == PlatformAccessRequestStatus.WAIVED;
            case VERIFIED -> to == PlatformAccessRequestStatus.NEEDS_REVISION;
            case WAIVED -> false;
        };
    }
}
