package com.brainserve.clientonboarding.onboarding.domain.model;

import java.util.EnumSet;

public final class StepStatePolicy {
    private StepStatePolicy() { }

    public static boolean permits(OnboardingStepInstance step, OnboardingStepInstance.Status target) {
        if (!step.applicable() || step.status() == target) return false;
        if (target == OnboardingStepInstance.Status.SKIPPED) return step.allowSkip()
                && EnumSet.of(OnboardingStepInstance.Status.AVAILABLE,
                OnboardingStepInstance.Status.IN_PROGRESS).contains(step.status());
        if (step.status() == OnboardingStepInstance.Status.COMPLETED
                && target == OnboardingStepInstance.Status.IN_PROGRESS) return step.allowReopen();
        return switch (step.status()) {
            // Dependency resolution is the only mechanism allowed to unlock a step.
            case LOCKED -> false;
            case AVAILABLE -> EnumSet.of(OnboardingStepInstance.Status.IN_PROGRESS,
                    OnboardingStepInstance.Status.COMPLETED, OnboardingStepInstance.Status.FAILED,
                    OnboardingStepInstance.Status.CANCELLED).contains(target)
                    && !(step.requiresReview() && target == OnboardingStepInstance.Status.COMPLETED);
            case IN_PROGRESS -> EnumSet.of(OnboardingStepInstance.Status.SUBMITTED,
                    OnboardingStepInstance.Status.COMPLETED, OnboardingStepInstance.Status.FAILED,
                    OnboardingStepInstance.Status.CANCELLED).contains(target)
                    && !(step.requiresReview() && target == OnboardingStepInstance.Status.COMPLETED);
            case SUBMITTED -> step.requiresReview()
                    ? target == OnboardingStepInstance.Status.UNDER_REVIEW
                    : target == OnboardingStepInstance.Status.COMPLETED;
            case UNDER_REVIEW -> target == OnboardingStepInstance.Status.COMPLETED
                    || target == OnboardingStepInstance.Status.NEEDS_REVISION;
            case NEEDS_REVISION -> target == OnboardingStepInstance.Status.IN_PROGRESS;
            case COMPLETED, SKIPPED, FAILED, CANCELLED -> false;
        };
    }
}
