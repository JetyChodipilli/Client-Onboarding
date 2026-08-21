package com.brainserve.onboarding.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brainserve.onboarding.onboarding.application.service.FinalReviewRevisionGate;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingReview;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingReviewAction;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingReviewRepository;
import com.brainserve.onboarding.workflow.domain.model.DependencyMode;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinalReviewRevisionGateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void selectedNonBlockingRevisionRemainsOutstandingUntilCompleted() {
        UUID org = UUID.randomUUID();
        UUID onboardingId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        OnboardingReviewRepository reviews = mock(OnboardingReviewRepository.class);
        var ids = mapper.createArrayNode().add(stepId.toString());
        when(reviews.findFirstByOrganizationIdAndOnboardingIdAndActionOrderByCreatedAtDescIdDesc(
                org, onboardingId, OnboardingReviewAction.REVISION_REQUESTED))
                .thenReturn(Optional.of(new OnboardingReview(UUID.randomUUID(), org, onboardingId, projectId,
                        OnboardingReviewAction.REVISION_REQUESTED, reviewer, "Please revise", ids, 4, Instant.now())));
        FinalReviewRevisionGate gate = new FinalReviewRevisionGate(reviews);

        OnboardingStepInstance step = step(org, onboardingId, stepId, OnboardingStepStatus.IN_PROGRESS);
        assertThat(gate.hasOutstanding(org, onboardingId, List.of(step))).isTrue();
        step.transitionTo(OnboardingStepStatus.COMPLETED, reviewer, Instant.now());
        assertThat(gate.hasOutstanding(org, onboardingId, List.of(step))).isFalse();
    }

    private OnboardingStepInstance step(UUID org, UUID onboardingId, UUID stepId, OnboardingStepStatus status) {
        return new OnboardingStepInstance(stepId, org, onboardingId, UUID.randomUUID(), UUID.randomUUID(),
                "OPTIONAL_REWORK", "Optional rework", null, WorkflowStepType.MANUAL_TASK, 0,
                false, false, true, false, DependencyMode.NONE, mapper.createObjectNode(), null,
                null, null, false, true, mapper.createObjectNode(), status, UUID.randomUUID(), Instant.now());
    }
}
