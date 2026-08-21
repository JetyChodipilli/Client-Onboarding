package com.brainserve.onboarding.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.tasks.domain.model.TaskItem;
import com.brainserve.onboarding.tasks.domain.model.TaskPriority;
import com.brainserve.onboarding.tasks.domain.model.TaskType;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskItemActorNormalizationTest {
    @Test
    void systemActorIsNormalizedBeforeAuditActorIdIsDerived() throws Exception {
        TaskItem task = new TaskItem(
                UUID.randomUUID(), UUID.randomUUID(), null, null, null,
                "System task", null, TaskType.SYSTEM_GENERATED, TaskPriority.MEDIUM,
                null, null, null, null, "system:test", UUID.randomUUID(), " system ", Instant.parse("2026-08-21T00:00:00Z"));

        assertThat(field(task, "createdByType")).isEqualTo("SYSTEM");
        assertThat(field(task, "createdBy")).isNull();
        assertThat(field(task, "updatedByType")).isEqualTo("SYSTEM");
        assertThat(field(task, "updatedBy")).isNull();
    }

    @Test
    void nonSystemActorRequiresActorIdBeforePersistence() {
        assertThatThrownBy(() -> new TaskItem(
                UUID.randomUUID(), UUID.randomUUID(), null, null, null,
                "Manual task", null, TaskType.MANUAL, TaskPriority.MEDIUM,
                null, null, null, null, null, null, "internal", Instant.parse("2026-08-21T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Actor id is required");
    }

    private static Object field(TaskItem task, String name) throws Exception {
        Field field = TaskItem.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(task);
    }
}
