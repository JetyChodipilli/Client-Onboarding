package com.brainserve.clientonboarding.onboarding.domain.model;

public final class OnboardingStatePolicy {
    private OnboardingStatePolicy() { }

    public static boolean permits(OnboardingInstance.Status from, OnboardingInstance.Status to) {
        if (from == to) return false;
        if (to == OnboardingInstance.Status.CANCELLED) {
            return from != OnboardingInstance.Status.COMPLETED && from != OnboardingInstance.Status.CANCELLED;
        }
        return switch (from) {
            case DRAFT -> to == OnboardingInstance.Status.INVITED;
            case INVITED -> to == OnboardingInstance.Status.IN_PROGRESS || to == OnboardingInstance.Status.EXPIRED;
            case IN_PROGRESS -> to == OnboardingInstance.Status.AWAITING_INTERNAL_REVIEW
                    || to == OnboardingInstance.Status.PAUSED;
            case AWAITING_INTERNAL_REVIEW -> to == OnboardingInstance.Status.NEEDS_REVISION
                    || to == OnboardingInstance.Status.APPROVED || to == OnboardingInstance.Status.PAUSED;
            case NEEDS_REVISION -> to == OnboardingInstance.Status.IN_PROGRESS
                    || to == OnboardingInstance.Status.PAUSED;
            case APPROVED -> to == OnboardingInstance.Status.COMPLETED;
            case PAUSED -> to == OnboardingInstance.Status.IN_PROGRESS
                    || to == OnboardingInstance.Status.AWAITING_INTERNAL_REVIEW;
            case COMPLETED, EXPIRED, CANCELLED -> false;
        };
    }
}
