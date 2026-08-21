package com.brainserve.onboarding.reporting.api.response;

public record ContractReportResponse(
        long contractsSent,
        long contractsSigned,
        long contractsDeclined,
        long contractsExpired,
        double completionRatePercent,
        Double averageSignatureHours) {
}
