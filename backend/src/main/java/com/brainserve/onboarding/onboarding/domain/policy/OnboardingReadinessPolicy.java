package com.brainserve.onboarding.onboarding.domain.policy;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import java.util.Collection;

/** PRD readiness rule: every applicable blocking step must be COMPLETED. */
public final class OnboardingReadinessPolicy {
    private OnboardingReadinessPolicy() {}

    public static boolean isReady(Collection<Requirement> applicableSteps) {
        return applicableSteps.stream()
                .filter(Requirement::blocking)
                .allMatch(step -> step.status() == OnboardingStepStatus.COMPLETED);
    }

    public record Requirement(boolean blocking, OnboardingStepStatus status) {}
}
