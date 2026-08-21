package com.brainserve.onboarding.reporting.api.response;

public record OperationalDashboardResponse(
        long totalOnboardings,
        long activeOnboardings,
        long awaitingClientAction,
        long awaitingInternalReview,
        long overdueSteps,
        long pendingPayments,
        long pendingContracts,
        long missingAssets,
        long missingAccess,
        long readyForApproval,
        long completedOnboardings,
        double completionRatePercent,
        Double averageOnboardingHours) {
}
