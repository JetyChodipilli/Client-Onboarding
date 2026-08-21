package com.brainserve.onboarding.reporting.api.response;

import java.time.Instant;
import java.util.UUID;

public record OnboardingReportRow(
        UUID onboardingId,
        UUID projectId,
        String projectName,
        UUID clientId,
        String clientName,
        String onboardingStatus,
        String projectStatus,
        int progressPercent,
        long overdueSteps,
        Instant startedAt,
        Instant completedAt) {
}
