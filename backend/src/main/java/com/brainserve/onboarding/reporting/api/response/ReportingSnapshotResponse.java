package com.brainserve.onboarding.reporting.api.response;

import java.time.Instant;
import java.util.List;

public record ReportingSnapshotResponse(
        Instant generatedAt,
        OperationalDashboardResponse operational,
        FinancialReportResponse financial,
        ContractReportResponse contracts,
        DurationReportResponse durations,
        List<FunnelMetricResponse> funnel) {
}
