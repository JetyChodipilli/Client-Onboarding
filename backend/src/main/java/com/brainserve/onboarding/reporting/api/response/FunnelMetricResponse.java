package com.brainserve.onboarding.reporting.api.response;

public record FunnelMetricResponse(
        String stepType,
        long total,
        long completed,
        long needsRevision,
        long failed,
        double completionRatePercent) {
}
