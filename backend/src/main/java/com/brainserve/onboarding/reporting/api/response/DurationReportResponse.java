package com.brainserve.onboarding.reporting.api.response;

public record DurationReportResponse(
        Double averageOnboardingHours,
        Double medianOnboardingHours,
        Double averageClientActionWaitingHours,
        Double averageInternalReviewWaitingHours,
        Double averageActivationHours) {
}
