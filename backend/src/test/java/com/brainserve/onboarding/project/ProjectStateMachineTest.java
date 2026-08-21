package com.brainserve.onboarding.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.project.domain.model.Project;
import com.brainserve.onboarding.project.domain.model.ProjectStateMachine;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectStateMachineTest {

    @Test
    void onlyDocumentedStatesCanBeHeldAndOnlyDocumentedHeldStatesResume() {
        assertThat(ProjectStateMachine.canHold(ProjectStatus.ONBOARDING)).isTrue();
        assertThat(ProjectStateMachine.canHold(ProjectStatus.READY)).isTrue();
        assertThat(ProjectStateMachine.canHold(ProjectStatus.ACTIVE)).isTrue();
        assertThat(ProjectStateMachine.canHold(ProjectStatus.DRAFT)).isFalse();
        assertThat(ProjectStateMachine.canResume(ProjectStatus.ONBOARDING)).isTrue();
        assertThat(ProjectStateMachine.canResume(ProjectStatus.ACTIVE)).isTrue();
        assertThat(ProjectStateMachine.canResume(ProjectStatus.READY)).isTrue();
    }

    @Test
    void draftCanCancelThenArchiveButCannotArchiveDirectly() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        Project project = new Project(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Website Relaunch", null, actor, now);

        assertThatThrownBy(() -> project.archive(actor, now.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        project.cancel(actor, now.plusSeconds(2));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELLED);
        project.archive(actor, now.plusSeconds(3));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    }

    @Test
    void clientAndServiceBecomeImmutableAfterOnboardingStarts() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        Project project = new Project(UUID.randomUUID(), UUID.randomUUID(), client, service, "Meta Ads", null, actor, now);
        project.transitionToOnboarding(actor, now.plusSeconds(1));

        assertThatThrownBy(() -> project.update(UUID.randomUUID(), service, "Meta Ads", null, actor, now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot change");
    }
    @Test
    void readyRequiresOnboardingAndActivationRequiresReady() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        Project project = new Project(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Launch", null, actor, now);

        assertThatThrownBy(() -> project.activate(actor, now.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        project.transitionToOnboarding(actor, now.plusSeconds(2));
        project.transitionToReady(actor, now.plusSeconds(3));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.READY);
        project.activate(actor, now.plusSeconds(4));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void readyProjectCanHoldAndResumeWithoutLosingReadyState() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID actor = UUID.randomUUID();
        Project project = new Project(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Launch", null, actor, now);
        project.transitionToOnboarding(actor, now.plusSeconds(1));
        project.transitionToReady(actor, now.plusSeconds(2));
        project.hold(actor, now.plusSeconds(3));
        assertThat(project.getHoldFromStatus()).isEqualTo(ProjectStatus.READY);
        project.resume(actor, now.plusSeconds(4));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.READY);
    }

}
