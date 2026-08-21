package com.brainserve.onboarding.project.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Project lifecycle policy from the PRD. Phase 2 exposes only lifecycle operations that do not
 * depend on onboarding/readiness: cancellation, hold/resume, and record archiving. Onboarding,
 * readiness, activation, and completion are driven by their owning later phases.
 */
public final class ProjectStateMachine {
    private static final Set<ProjectStatus> CANCELLABLE = EnumSet.of(
            ProjectStatus.DRAFT, ProjectStatus.ONBOARDING, ProjectStatus.READY, ProjectStatus.ACTIVE, ProjectStatus.ON_HOLD);
    private static final Set<ProjectStatus> ARCHIVABLE = EnumSet.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED);

    private ProjectStateMachine() {}

    public static boolean canCancel(ProjectStatus current) {
        return CANCELLABLE.contains(current);
    }

    public static boolean canArchive(ProjectStatus current) {
        return ARCHIVABLE.contains(current);
    }

    public static boolean canHold(ProjectStatus current) {
        return current == ProjectStatus.ONBOARDING || current == ProjectStatus.READY || current == ProjectStatus.ACTIVE;
    }

    public static boolean canResume(ProjectStatus heldFrom) {
        return heldFrom == ProjectStatus.ONBOARDING || heldFrom == ProjectStatus.READY || heldFrom == ProjectStatus.ACTIVE;
    }
}
