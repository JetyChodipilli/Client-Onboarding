package com.brainserve.clientonboarding.workflow.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TemplateStep(UUID id, UUID organizationId, UUID templateVersionId, String stepKey, String name,
                           String description, StepType stepType, int displayOrder, boolean required,
                           boolean blocking, boolean clientVisible, boolean requiresReview,
                           DependencyMode dependencyMode, WorkflowCondition condition, String assignedRole,
                           Integer dueAfterHours, UUID reminderPolicyId, boolean allowSkip, boolean allowReopen,
                           Map<String, Object> configuration, List<UUID> dependencyStepIds,
                           Instant createdAt, Instant updatedAt, long version) {
    public enum StepType { WELCOME, INSTRUCTION, FORM, FILE_UPLOAD, PAYMENT, CONTRACT, PLATFORM_ACCESS,
        MANUAL_TASK, APPROVAL, EXTERNAL_LINK, VIDEO_GUIDE, MEETING, CUSTOM }
    public enum DependencyMode { NONE, ALL, ANY }

    public TemplateStep {
        configuration = configuration == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        dependencyStepIds = dependencyStepIds == null ? List.of() : List.copyOf(dependencyStepIds);
    }
}
