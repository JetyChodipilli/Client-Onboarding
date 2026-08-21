package com.brainserve.onboarding.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnboardingReadinessLifecycleTest {
    @Test
    void readinessAutomaticallyEntersFinalReviewAndLossReturnsToRevision() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        OnboardingInstance onboarding = new OnboardingInstance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Standard onboarding", 1, "a".repeat(64), "b".repeat(64), actor, now);
        onboarding.transitionTo(OnboardingStatus.INVITED, actor, now.plusSeconds(1));
        onboarding.transitionTo(OnboardingStatus.IN_PROGRESS, actor, now.plusSeconds(2));

        onboarding.updateReadiness(true, actor, now.plusSeconds(3));
        assertThat(onboarding.isReady()).isTrue();
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.AWAITING_INTERNAL_REVIEW);

        onboarding.updateReadiness(false, actor, now.plusSeconds(4));
        assertThat(onboarding.isReady()).isFalse();
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.NEEDS_REVISION);
    }

    @Test
    void revisionCanResumeWorkAndReturnToFinalReview() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        OnboardingInstance onboarding = new OnboardingInstance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Standard onboarding", 1, "a".repeat(64), "b".repeat(64), actor, now);
        onboarding.transitionTo(OnboardingStatus.INVITED, actor, now.plusSeconds(1));
        onboarding.transitionTo(OnboardingStatus.IN_PROGRESS, actor, now.plusSeconds(2));
        onboarding.updateReadiness(true, actor, now.plusSeconds(3));
        onboarding.transitionTo(OnboardingStatus.NEEDS_REVISION, actor, now.plusSeconds(4));
        onboarding.transitionTo(OnboardingStatus.IN_PROGRESS, actor, now.plusSeconds(5));
        onboarding.updateReadiness(true, actor, now.plusSeconds(6));
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.AWAITING_INTERNAL_REVIEW);
    }
    @Test
    void nonBlockingFinalReviewRevisionDoesNotPrematurelyReturnToReview() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        OnboardingInstance onboarding = new OnboardingInstance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Standard onboarding", 1, "a".repeat(64), "b".repeat(64), actor, now);
        onboarding.transitionTo(OnboardingStatus.INVITED, actor, now.plusSeconds(1));
        onboarding.transitionTo(OnboardingStatus.IN_PROGRESS, actor, now.plusSeconds(2));
        onboarding.updateReadiness(true, actor, now.plusSeconds(3));
        onboarding.transitionTo(OnboardingStatus.NEEDS_REVISION, actor, now.plusSeconds(4));
        onboarding.transitionTo(OnboardingStatus.IN_PROGRESS, actor, now.plusSeconds(5));

        onboarding.updateReadiness(true, true, actor, "INTERNAL", now.plusSeconds(6));
        assertThat(onboarding.isReady()).isTrue();
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.IN_PROGRESS);

        onboarding.updateReadiness(true, false, actor, "INTERNAL", now.plusSeconds(7));
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.AWAITING_INTERNAL_REVIEW);
    }

}
