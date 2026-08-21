package com.brainserve.onboarding.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStateMachine;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStateMachine;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import org.junit.jupiter.api.Test;

class OnboardingStateMachineTest {
    @Test
    void onboardingLifecycleRejectsInvalidTransitions() {
        assertThat(OnboardingStateMachine.canTransition(OnboardingStatus.DRAFT, OnboardingStatus.INVITED)).isTrue();
        assertThat(OnboardingStateMachine.canTransition(OnboardingStatus.DRAFT, OnboardingStatus.COMPLETED)).isFalse();
        assertThat(OnboardingStateMachine.canTransition(OnboardingStatus.AWAITING_INTERNAL_REVIEW, OnboardingStatus.NEEDS_REVISION)).isTrue();
        assertThat(OnboardingStateMachine.canTransition(OnboardingStatus.COMPLETED, OnboardingStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void reviewStepsCannotBypassSubmitAndReview() {
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.AVAILABLE, OnboardingStepStatus.COMPLETED, true, false, false)).isFalse();
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.AVAILABLE, OnboardingStepStatus.IN_PROGRESS, true, false, false)).isTrue();
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.IN_PROGRESS, OnboardingStepStatus.SUBMITTED, true, false, false)).isTrue();
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.SUBMITTED, OnboardingStepStatus.UNDER_REVIEW, true, false, false)).isTrue();
    }

    @Test
    void skipAndReopenHonorConfiguration() {
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.AVAILABLE, OnboardingStepStatus.SKIPPED, false, true, false)).isTrue();
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.COMPLETED, OnboardingStepStatus.IN_PROGRESS, false, false, false)).isFalse();
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.COMPLETED, OnboardingStepStatus.IN_PROGRESS, false, false, true)).isTrue();
        assertThat(OnboardingStepStateMachine.canTransition(OnboardingStepStatus.COMPLETED, OnboardingStepStatus.NEEDS_REVISION, false, false, true)).isTrue();
    }
}
