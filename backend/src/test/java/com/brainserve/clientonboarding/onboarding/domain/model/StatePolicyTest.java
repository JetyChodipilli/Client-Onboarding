package com.brainserve.clientonboarding.onboarding.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatePolicyTest {

    @Test
    void dependencyEngineAloneUnlocksLockedSteps() {
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.LOCKED, false, false, false),
                OnboardingStepInstance.Status.AVAILABLE)).isFalse();
    }

    @Test
    void reviewStepsCannotBypassSubmissionAndReview() {
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.IN_PROGRESS, true, false, false),
                OnboardingStepInstance.Status.COMPLETED)).isFalse();
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.IN_PROGRESS, true, false, false),
                OnboardingStepInstance.Status.SUBMITTED)).isTrue();
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.SUBMITTED, true, false, false),
                OnboardingStepInstance.Status.UNDER_REVIEW)).isTrue();
    }

    @Test
    void skipAndReopenRulesAreExplicit() {
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.AVAILABLE, false, true, false),
                OnboardingStepInstance.Status.SKIPPED)).isTrue();
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.COMPLETED, false, false, true),
                OnboardingStepInstance.Status.IN_PROGRESS)).isTrue();
        assertThat(StepStatePolicy.permits(step(OnboardingStepInstance.Status.COMPLETED, false, false, false),
                OnboardingStepInstance.Status.IN_PROGRESS)).isFalse();
    }

    @Test
    void onboardingLifecycleRejectsNegativeTransitions() {
        assertThat(OnboardingStatePolicy.permits(OnboardingInstance.Status.DRAFT,
                OnboardingInstance.Status.IN_PROGRESS)).isFalse();
        assertThat(OnboardingStatePolicy.permits(OnboardingInstance.Status.DRAFT,
                OnboardingInstance.Status.INVITED)).isTrue();
        assertThat(OnboardingStatePolicy.permits(OnboardingInstance.Status.COMPLETED,
                OnboardingInstance.Status.CANCELLED)).isFalse();
    }

    private OnboardingStepInstance step(OnboardingStepInstance.Status status, boolean review,
                                        boolean allowSkip, boolean allowReopen) {
        return new OnboardingStepInstance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "STEP", "Step", null, TemplateStep.StepType.MANUAL_TASK, 0, true, true, true, review,
                TemplateStep.DependencyMode.NONE, null, null, null, allowSkip, allowReopen, Map.of(), true,
                status, List.of(), null, Instant.EPOCH, Instant.EPOCH, 0);
    }
}
