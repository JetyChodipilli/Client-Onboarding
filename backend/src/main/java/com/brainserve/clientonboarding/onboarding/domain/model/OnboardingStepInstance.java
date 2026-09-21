package com.brainserve.clientonboarding.onboarding.domain.model;

import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowCondition;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OnboardingStepInstance(UUID id, UUID organizationId, UUID onboardingId, UUID sourceStepId,
                                     String stepKey, String name, String description,
                                     TemplateStep.StepType stepType, int displayOrder, boolean required,
                                     boolean blocking, boolean clientVisible, boolean requiresReview,
                                     TemplateStep.DependencyMode dependencyMode, WorkflowCondition condition,
                                     String assignedRole, Instant dueAt, boolean allowSkip, boolean allowReopen,
                                     Map<String, Object> configuration, boolean applicable, Status status,
                                     List<UUID> dependencyStepInstanceIds, Instant completedAt,
                                     Instant createdAt, Instant updatedAt, long version) {
    public enum Status { LOCKED, AVAILABLE, IN_PROGRESS, SUBMITTED, UNDER_REVIEW, NEEDS_REVISION,
        COMPLETED, SKIPPED, FAILED, CANCELLED }

    public OnboardingStepInstance {
        configuration = configuration == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        dependencyStepInstanceIds = dependencyStepInstanceIds == null ? List.of()
                : List.copyOf(dependencyStepInstanceIds);
    }
}
