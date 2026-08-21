package com.brainserve.onboarding.onboarding.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Server-side onboarding lifecycle validator. Public Phase 3 APIs expose only DRAFT creation/read. */
public final class OnboardingStateMachine {
    private OnboardingStateMachine() {}

    public static boolean canTransition(OnboardingStatus from, OnboardingStatus to) {
        if (from == to) return false;
        return switch (from) {
            case DRAFT -> to == OnboardingStatus.INVITED || to == OnboardingStatus.CANCELLED;
            case INVITED -> Set.of(OnboardingStatus.IN_PROGRESS, OnboardingStatus.EXPIRED, OnboardingStatus.CANCELLED, OnboardingStatus.PAUSED).contains(to);
            case IN_PROGRESS -> Set.of(OnboardingStatus.AWAITING_INTERNAL_REVIEW, OnboardingStatus.PAUSED, OnboardingStatus.CANCELLED).contains(to);
            case AWAITING_INTERNAL_REVIEW -> Set.of(OnboardingStatus.NEEDS_REVISION, OnboardingStatus.APPROVED, OnboardingStatus.PAUSED, OnboardingStatus.CANCELLED).contains(to);
            case NEEDS_REVISION -> Set.of(OnboardingStatus.IN_PROGRESS, OnboardingStatus.PAUSED, OnboardingStatus.CANCELLED).contains(to);
            case APPROVED -> to == OnboardingStatus.COMPLETED;
            case PAUSED -> EnumSet.of(OnboardingStatus.INVITED, OnboardingStatus.IN_PROGRESS, OnboardingStatus.AWAITING_INTERNAL_REVIEW, OnboardingStatus.NEEDS_REVISION, OnboardingStatus.CANCELLED).contains(to);
            case COMPLETED, EXPIRED, CANCELLED -> false;
        };
    }
}
