package com.brainserve.clientonboarding.workflow.domain.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkflowGraphPolicy {
    private WorkflowGraphPolicy() { }

    public static void validate(List<TemplateStep> steps) {
        if (steps.isEmpty()) throw invalid("A workflow version must contain at least one step.");
        Map<UUID, TemplateStep> byId = new HashMap<>();
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (TemplateStep step : steps) {
            if (byId.put(step.id(), step) != null || !keys.add(step.stepKey()) || !orders.add(step.displayOrder())) {
                throw invalid("Step IDs, keys, and display order values must be unique.");
            }
            boolean hasDependencies = !step.dependencyStepIds().isEmpty();
            if ((step.dependencyMode() == TemplateStep.DependencyMode.NONE) == hasDependencies) {
                throw invalid("Dependency mode NONE must have no dependencies; ALL or ANY must have at least one.");
            }
            if (step.blocking() && step.allowSkip()) {
                throw invalid("A blocking step cannot be skippable because readiness requires completion.");
            }
        }
        for (TemplateStep step : steps) {
            if (new HashSet<>(step.dependencyStepIds()).size() != step.dependencyStepIds().size()) {
                throw invalid("A step cannot declare the same dependency more than once.");
            }
            if (!byId.keySet().containsAll(step.dependencyStepIds()) || step.dependencyStepIds().contains(step.id())) {
                throw invalid("Every dependency must reference another step in the same version.");
            }
        }
        Map<UUID, Integer> colors = new HashMap<>();
        for (TemplateStep step : steps) visit(step.id(), byId, colors);
    }

    private static void visit(UUID id, Map<UUID, TemplateStep> steps, Map<UUID, Integer> colors) {
        int color = colors.getOrDefault(id, 0);
        if (color == 1) throw invalid("Workflow dependencies cannot contain a cycle.");
        if (color == 2) return;
        colors.put(id, 1);
        for (UUID dependency : steps.get(id).dependencyStepIds()) visit(dependency, steps, colors);
        colors.put(id, 2);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
