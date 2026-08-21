package com.brainserve.onboarding.tasks.domain.model;
import java.util.Set;
public final class TaskStateMachine {
    private TaskStateMachine() {}
    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == to) return true;
        return switch (from) {
            case TODO -> Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.IN_REVIEW, TaskStatus.COMPLETED, TaskStatus.CANCELLED).contains(to);
            case IN_PROGRESS -> Set.of(TaskStatus.TODO, TaskStatus.BLOCKED, TaskStatus.IN_REVIEW, TaskStatus.COMPLETED, TaskStatus.CANCELLED).contains(to);
            case BLOCKED -> Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED).contains(to);
            case IN_REVIEW -> Set.of(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED, TaskStatus.CANCELLED).contains(to);
            case COMPLETED, CANCELLED -> false;
        };
    }
}
