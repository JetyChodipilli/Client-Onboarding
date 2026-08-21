package com.brainserve.onboarding.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.domain.policy.OnboardingReadinessPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class OnboardingReadinessPolicyTest {
    @Test
    void onlyApplicableBlockingStepsDetermineReadiness() {
        assertThat(OnboardingReadinessPolicy.isReady(List.of(
                new OnboardingReadinessPolicy.Requirement(true, OnboardingStepStatus.COMPLETED),
                new OnboardingReadinessPolicy.Requirement(false, OnboardingStepStatus.AVAILABLE)))).isTrue();
        assertThat(OnboardingReadinessPolicy.isReady(List.of(
                new OnboardingReadinessPolicy.Requirement(true, OnboardingStepStatus.AVAILABLE),
                new OnboardingReadinessPolicy.Requirement(false, OnboardingStepStatus.COMPLETED)))).isFalse();
    }

    @Test
    void zeroBlockingStepsAreReadyByDefinition() {
        assertThat(OnboardingReadinessPolicy.isReady(List.of(
                new OnboardingReadinessPolicy.Requirement(false, OnboardingStepStatus.AVAILABLE)))).isTrue();
    }
}
