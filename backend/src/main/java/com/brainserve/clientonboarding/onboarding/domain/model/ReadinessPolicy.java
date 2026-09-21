package com.brainserve.clientonboarding.onboarding.domain.model;

import java.util.List;

public final class ReadinessPolicy {
    private ReadinessPolicy() { }

    public static boolean ready(List<OnboardingStepInstance> steps) {
        return steps.stream().filter(OnboardingStepInstance::applicable)
                .filter(OnboardingStepInstance::blocking)
                .allMatch(step -> step.status() == OnboardingStepInstance.Status.COMPLETED);
    }

    public static int progress(List<OnboardingStepInstance> steps) {
        long total = steps.stream().filter(OnboardingStepInstance::applicable).filter(step -> step.required()
                || step.blocking()).count();
        if (total == 0) return 100;
        long complete = steps.stream().filter(OnboardingStepInstance::applicable)
                .filter(step -> step.required() || step.blocking())
                .filter(step -> step.status() == OnboardingStepInstance.Status.COMPLETED
                        || !step.blocking() && step.status() == OnboardingStepInstance.Status.SKIPPED).count();
        return (int) (complete * 100 / total);
    }
}
