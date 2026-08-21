package com.brainserve.onboarding.reminders.api.request;import jakarta.validation.constraints.Min;public record ReminderVersionRequest(@Min(0)long version){}
