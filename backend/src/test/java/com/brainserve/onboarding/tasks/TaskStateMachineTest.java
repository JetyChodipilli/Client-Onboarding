package com.brainserve.onboarding.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.tasks.domain.model.TaskStateMachine;
import com.brainserve.onboarding.tasks.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

class TaskStateMachineTest {
    @Test void supportsReviewLoopAndTerminalProtection() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.TODO, TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.IN_REVIEW, TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.IN_REVIEW, TaskStatus.COMPLETED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.CANCELLED, TaskStatus.TODO)).isFalse();
    }
}
