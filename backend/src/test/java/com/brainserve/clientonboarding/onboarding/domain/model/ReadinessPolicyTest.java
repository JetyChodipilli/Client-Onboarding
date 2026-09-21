package com.brainserve.clientonboarding.onboarding.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadinessPolicyTest {

    @Test
    void onlyApplicableBlockingStepsDetermineReadiness() {
        var optionalIncomplete = step(false, false, true, OnboardingStepInstance.Status.AVAILABLE);
        var inapplicableBlocker = step(true, true, false, OnboardingStepInstance.Status.LOCKED);
        var completeBlocker = step(true, true, true, OnboardingStepInstance.Status.COMPLETED);

        assertThat(ReadinessPolicy.ready(List.of(optionalIncomplete, inapplicableBlocker, completeBlocker))).isTrue();
        assertThat(ReadinessPolicy.ready(List.of(optionalIncomplete, inapplicableBlocker,
                step(true, true, true, OnboardingStepInstance.Status.IN_PROGRESS)))).isFalse();
        assertThat(ReadinessPolicy.ready(List.of(
                step(true, true, true, OnboardingStepInstance.Status.SKIPPED)))).isFalse();
    }

    @Test
    void progressDoesNotPresentSkippedBlockingWorkAsComplete() {
        assertThat(ReadinessPolicy.progress(List.of(
                step(true, true, true, OnboardingStepInstance.Status.SKIPPED),
                step(true, false, true, OnboardingStepInstance.Status.SKIPPED)))).isEqualTo(50);
    }

    private OnboardingStepInstance step(boolean required, boolean blocking, boolean applicable,
                                        OnboardingStepInstance.Status status) {
        UUID id = UUID.randomUUID();
        return new OnboardingStepInstance(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "STEP", "Step", null, TemplateStep.StepType.MANUAL_TASK, 0, required, blocking, true, false,
                TemplateStep.DependencyMode.NONE, null, null, null, true, true, Map.of(), applicable, status,
                List.of(), null, Instant.EPOCH, Instant.EPOCH, 0);
    }
}
