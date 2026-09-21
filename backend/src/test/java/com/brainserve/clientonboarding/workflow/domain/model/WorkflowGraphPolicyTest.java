package com.brainserve.clientonboarding.workflow.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowGraphPolicyTest {
    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();

    @Test
    void acceptsSequentialParallelAllAndAnyGraphs() {
        TemplateStep first = step("FIRST", 0, TemplateStep.DependencyMode.NONE, List.of());
        TemplateStep second = step("SECOND", 1, TemplateStep.DependencyMode.NONE, List.of());
        TemplateStep all = step("ALL", 2, TemplateStep.DependencyMode.ALL, List.of(first.id(), second.id()));
        TemplateStep any = step("ANY", 3, TemplateStep.DependencyMode.ANY, List.of(first.id(), second.id()));

        assertThatCode(() -> WorkflowGraphPolicy.validate(List.of(first, second, all, any)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDanglingDependenciesAndCycles() {
        TemplateStep dangling = step("DANGLING", 0, TemplateStep.DependencyMode.ALL, List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> WorkflowGraphPolicy.validate(List.of(dangling)))
                .isInstanceOf(IllegalArgumentException.class);

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        TemplateStep first = step(firstId, "FIRST", 0, TemplateStep.DependencyMode.ALL, List.of(secondId));
        TemplateStep second = step(secondId, "SECOND", 1, TemplateStep.DependencyMode.ALL, List.of(firstId));
        assertThatThrownBy(() -> WorkflowGraphPolicy.validate(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsDependencyModesThatDoNotMatchTheirEdges() {
        TemplateStep first = step("FIRST", 0, TemplateStep.DependencyMode.NONE, List.of());
        TemplateStep invalid = step("INVALID", 1, TemplateStep.DependencyMode.NONE, List.of(first.id()));
        assertThatThrownBy(() -> WorkflowGraphPolicy.validate(List.of(first, invalid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateDependencyEdgesBeforePersistence() {
        TemplateStep first = step("FIRST", 0, TemplateStep.DependencyMode.NONE, List.of());
        TemplateStep duplicate = step("DUPLICATE", 1, TemplateStep.DependencyMode.ALL,
                List.of(first.id(), first.id()));

        assertThatThrownBy(() -> WorkflowGraphPolicy.validate(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same dependency");
    }

    @Test
    void rejectsSkippableBlockingStepsThatCanNeverSatisfyReadiness() {
        TemplateStep invalid = new TemplateStep(UUID.randomUUID(), ORGANIZATION, VERSION, "BLOCKER", "Blocker",
                null, TemplateStep.StepType.MANUAL_TASK, 0, true, true, true, false,
                TemplateStep.DependencyMode.NONE, null, null, null, null, true, false, Map.of(), List.of(),
                Instant.EPOCH, Instant.EPOCH, 0);

        assertThatThrownBy(() -> WorkflowGraphPolicy.validate(List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be skippable");
    }

    private TemplateStep step(String key, int order, TemplateStep.DependencyMode mode, List<UUID> dependencies) {
        return step(UUID.randomUUID(), key, order, mode, dependencies);
    }

    private TemplateStep step(UUID id, String key, int order, TemplateStep.DependencyMode mode,
                              List<UUID> dependencies) {
        return new TemplateStep(id, ORGANIZATION, VERSION, key, key, null, TemplateStep.StepType.MANUAL_TASK,
                order, true, true, true, false, mode, null, null, null, null,
                false, false, Map.of(), dependencies, Instant.EPOCH, Instant.EPOCH, 0);
    }
}
