package com.brainserve.clientonboarding.portal.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance.Status.*;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance.Status;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep.StepType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClientStepPolicyTest {
    @Test
    void clientCanStartAndReviseButCannotSelfApprove() {
        assertThat(ClientStepPolicy.permits(AVAILABLE, IN_PROGRESS, true)).isTrue();
        assertThat(ClientStepPolicy.permits(NEEDS_REVISION, IN_PROGRESS, true)).isTrue();
        assertThat(ClientStepPolicy.permits(IN_PROGRESS, SUBMITTED, true)).isTrue();
        assertThat(ClientStepPolicy.permits(IN_PROGRESS, COMPLETED, true)).isFalse();
        assertThat(ClientStepPolicy.permits(IN_PROGRESS, COMPLETED, false)).isTrue();
    }

    @Test
    void clientCannotUnlockReviewReopenSkipOrCancelWork() {
        for (Status state : Set.of(LOCKED, SUBMITTED, UNDER_REVIEW, COMPLETED, SKIPPED, FAILED, CANCELLED)) {
            assertThat(ClientStepPolicy.actionable(state)).isFalse();
            for (Status target : Status.values()) assertThat(ClientStepPolicy.permits(state, target, false)).isFalse();
        }
        for (Status target : Set.of(SKIPPED, FAILED, CANCELLED, UNDER_REVIEW)) {
            assertThat(ClientStepPolicy.permits(IN_PROGRESS, target, false)).isFalse();
        }
    }

    @Test
    void evidenceCollectionRequiresItsOwnSecureHandler() {
        for (StepType type : Set.of(StepType.PAYMENT, StepType.CONTRACT, StepType.FILE_UPLOAD,
                StepType.FORM, StepType.PLATFORM_ACCESS, StepType.APPROVAL, StepType.MANUAL_TASK)) {
            assertThat(ClientStepPolicy.informational(type)).isFalse();
        }
        assertThat(ClientStepPolicy.informational(StepType.WELCOME)).isTrue();
    }
}
