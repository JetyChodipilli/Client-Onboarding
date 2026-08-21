package com.brainserve.onboarding.onboarding.domain.model;

/** Central validator for workflow-step state transitions. */
public final class OnboardingStepStateMachine {
    private OnboardingStepStateMachine() {}

    public static boolean canTransition(OnboardingStepStatus from, OnboardingStepStatus to,
                                        boolean requiresReview, boolean allowSkip, boolean allowReopen) {
        if (from == to) return false;
        return switch (from) {
            case LOCKED -> to == OnboardingStepStatus.AVAILABLE || to == OnboardingStepStatus.CANCELLED;
            case AVAILABLE -> to == OnboardingStepStatus.IN_PROGRESS
                    || (!requiresReview && to == OnboardingStepStatus.COMPLETED)
                    || to == OnboardingStepStatus.FAILED
                    || to == OnboardingStepStatus.CANCELLED
                    || (allowSkip && to == OnboardingStepStatus.SKIPPED);
            case IN_PROGRESS -> to == OnboardingStepStatus.SUBMITTED
                    || (!requiresReview && to == OnboardingStepStatus.COMPLETED)
                    || to == OnboardingStepStatus.FAILED
                    || to == OnboardingStepStatus.CANCELLED
                    || (allowSkip && to == OnboardingStepStatus.SKIPPED);
            case SUBMITTED -> (requiresReview && to == OnboardingStepStatus.UNDER_REVIEW)
                    || (!requiresReview && to == OnboardingStepStatus.COMPLETED)
                    || to == OnboardingStepStatus.CANCELLED;
            case UNDER_REVIEW -> to == OnboardingStepStatus.COMPLETED
                    || to == OnboardingStepStatus.NEEDS_REVISION
                    || to == OnboardingStepStatus.FAILED
                    || to == OnboardingStepStatus.CANCELLED;
            case NEEDS_REVISION -> to == OnboardingStepStatus.IN_PROGRESS || to == OnboardingStepStatus.CANCELLED;
            case COMPLETED, SKIPPED -> allowReopen
                    && (to == OnboardingStepStatus.IN_PROGRESS || to == OnboardingStepStatus.NEEDS_REVISION);
            case FAILED -> to == OnboardingStepStatus.IN_PROGRESS || to == OnboardingStepStatus.CANCELLED;
            case CANCELLED -> false;
        };
    }
}
