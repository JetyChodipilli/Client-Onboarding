package com.brainserve.onboarding.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.reminders.domain.model.ScheduledReminder;
import com.brainserve.onboarding.reminders.domain.model.ScheduledReminderStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduledReminderTest {
    @Test void maximumCountCompletesScheduleAndReactivationClearsSuppression() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        ScheduledReminder reminder = new ScheduledReminder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(60), now);
        reminder.sent(now.plusSeconds(60), now.plusSeconds(120), 1);
        assertThat(reminder.getStatus()).isEqualTo(ScheduledReminderStatus.COMPLETED);
        assertThat(reminder.getSentCount()).isEqualTo(1);
        assertThat(reminder.getSuppressionReason()).contains("Maximum");

        reminder.reactivate(now.plusSeconds(180), now.plusSeconds(121));
        assertThat(reminder.getStatus()).isEqualTo(ScheduledReminderStatus.ACTIVE);
        assertThat(reminder.getSuppressionReason()).isNull();
    }

    @Test void completedOrCancelledWorkCanBeSuppressedExplicitly() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        ScheduledReminder reminder = new ScheduledReminder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(60), now);
        reminder.complete("Workflow step is completed", now.plusSeconds(1));
        assertThat(reminder.getStatus()).isEqualTo(ScheduledReminderStatus.COMPLETED);
        assertThat(reminder.getSuppressionReason()).contains("completed");
    }
}
