package com.brainserve.onboarding.reporting.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.reporting.api.response.ContractReportResponse;
import com.brainserve.onboarding.reporting.api.response.DurationReportResponse;
import com.brainserve.onboarding.reporting.api.response.FinancialCurrencyResponse;
import com.brainserve.onboarding.reporting.api.response.FinancialReportResponse;
import com.brainserve.onboarding.reporting.api.response.FunnelMetricResponse;
import com.brainserve.onboarding.reporting.api.response.OnboardingReportRow;
import com.brainserve.onboarding.reporting.api.response.OperationalDashboardResponse;
import com.brainserve.onboarding.reporting.api.response.ReportingSnapshotResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped reporting read model. It intentionally owns no business tables and never mutates sibling modules.
 * All SQL predicates include organization_id so reporting cannot become a tenant-isolation bypass.
 */
@Service
public class ReportingService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ONBOARDING_STATUSES = Set.of("DRAFT","INVITED","IN_PROGRESS","AWAITING_INTERNAL_REVIEW","NEEDS_REVISION","APPROVED","COMPLETED","PAUSED","EXPIRED","CANCELLED");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReportingService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportingSnapshotResponse snapshot(UUID organizationId) {
        return new ReportingSnapshotResponse(clock.instant(), operational(organizationId), financial(organizationId),
                contracts(organizationId), durations(organizationId), funnel(organizationId));
    }

    @Transactional(readOnly = true)
    public OperationalDashboardResponse operational(UUID organizationId) {
        return jdbc.queryForObject("""
                WITH onboarding AS (
                    SELECT count(*) AS total,
                           count(*) FILTER (WHERE status NOT IN ('COMPLETED','CANCELLED','EXPIRED')) AS active,
                           count(*) FILTER (WHERE status='AWAITING_INTERNAL_REVIEW') AS awaiting_review,
                           count(*) FILTER (WHERE status='AWAITING_INTERNAL_REVIEW' AND ready) AS ready_for_approval,
                           count(*) FILTER (WHERE status='COMPLETED') AS completed,
                           avg(EXTRACT(EPOCH FROM (completed_at-started_at))/3600.0)
                               FILTER (WHERE status='COMPLETED' AND completed_at IS NOT NULL) AS avg_hours
                    FROM client_onboarding.onboarding_instances WHERE organization_id=?
                ), client_action AS (
                    SELECT count(DISTINCT s.onboarding_id) AS value
                    FROM client_onboarding.onboarding_step_instances s
                    JOIN client_onboarding.onboarding_instances o
                      ON o.organization_id=s.organization_id AND o.id=s.onboarding_id
                    WHERE s.organization_id=? AND s.client_visible=true
                      AND s.status IN ('AVAILABLE','IN_PROGRESS','NEEDS_REVISION')
                      AND o.status NOT IN ('COMPLETED','CANCELLED','EXPIRED','PAUSED')
                ), overdue AS (
                    SELECT count(*) AS value
                    FROM client_onboarding.onboarding_step_instances s
                    JOIN client_onboarding.onboarding_instances o
                      ON o.organization_id=s.organization_id AND o.id=s.onboarding_id
                    WHERE s.organization_id=? AND s.due_at<CURRENT_TIMESTAMP
                      AND s.status NOT IN ('COMPLETED','SKIPPED','CANCELLED')
                      AND o.status NOT IN ('COMPLETED','CANCELLED','EXPIRED','PAUSED')
                ), payment AS (
                    SELECT count(*) AS value FROM client_onboarding.invoices
                    WHERE organization_id=? AND status IN ('SENT','VIEWED','PARTIALLY_PAID','OVERDUE')
                ), contract AS (
                    SELECT count(*) AS value FROM client_onboarding.contracts
                    WHERE organization_id=? AND status IN ('SENT','VIEWED')
                ), asset AS (
                    SELECT count(*) AS value
                    FROM client_onboarding.asset_requirements ar
                    JOIN client_onboarding.onboarding_instances o
                      ON o.organization_id=ar.organization_id AND o.id=ar.onboarding_id
                    WHERE ar.organization_id=? AND ar.required=true
                      AND o.status NOT IN ('COMPLETED','CANCELLED','EXPIRED','PAUSED')
                      AND NOT EXISTS (
                        SELECT 1 FROM client_onboarding.assets a
                        WHERE a.organization_id=ar.organization_id AND a.requirement_id=ar.id AND a.status='APPROVED')
                ), access_req AS (
                    SELECT count(*) AS value
                    FROM client_onboarding.platform_access_requests r
                    JOIN client_onboarding.onboarding_instances o
                      ON o.organization_id=r.organization_id AND o.id=r.onboarding_id
                    WHERE r.organization_id=? AND r.status NOT IN ('VERIFIED','WAIVED')
                      AND o.status NOT IN ('COMPLETED','CANCELLED','EXPIRED','PAUSED')
                )
                SELECT onboarding.*, client_action.value client_action_value, overdue.value overdue_value,
                       payment.value payment_value, contract.value contract_value, asset.value asset_value,
                       access_req.value access_value
                FROM onboarding, client_action, overdue, payment, contract, asset, access_req
                """, (rs, row) -> {
            long total = rs.getLong("total");
            long completed = rs.getLong("completed");
            return new OperationalDashboardResponse(total, rs.getLong("active"), rs.getLong("client_action_value"),
                    rs.getLong("awaiting_review"), rs.getLong("overdue_value"), rs.getLong("payment_value"),
                    rs.getLong("contract_value"), rs.getLong("asset_value"), rs.getLong("access_value"),
                    rs.getLong("ready_for_approval"), completed,
                    percent(completed, total), nullableDouble(rs, "avg_hours"));
        }, organizationId, organizationId, organizationId, organizationId, organizationId, organizationId, organizationId);
    }

    @Transactional(readOnly = true)
    public List<FunnelMetricResponse> funnel(UUID organizationId) {
        return jdbc.query("""
                SELECT step_type, count(*) total,
                       count(*) FILTER (WHERE status='COMPLETED') completed,
                       count(*) FILTER (WHERE status='NEEDS_REVISION') needs_revision,
                       count(*) FILTER (WHERE status='FAILED') failed
                FROM client_onboarding.onboarding_step_instances
                WHERE organization_id=?
                GROUP BY step_type
                ORDER BY count(*) DESC, step_type
                """, (rs, row) -> {
            long total = rs.getLong("total");
            long completed = rs.getLong("completed");
            return new FunnelMetricResponse(rs.getString("step_type"), total, completed,
                    rs.getLong("needs_revision"), rs.getLong("failed"), percent(completed, total));
        }, organizationId);
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse financial(UUID organizationId) {
        FinancialTotals totals = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE sent_at IS NOT NULL) AS sent,
                       count(*) FILTER (WHERE status IN ('PAID','REFUNDED','PARTIALLY_REFUNDED')) AS paid,
                       count(*) FILTER (WHERE status='OVERDUE') AS overdue,
                       count(*) FILTER (WHERE status='PARTIALLY_PAID') AS partial,
                       avg(EXTRACT(EPOCH FROM (paid_at-sent_at))/3600.0)
                         FILTER (WHERE paid_at IS NOT NULL AND sent_at IS NOT NULL) AS avg_payment_hours
                FROM client_onboarding.invoices WHERE organization_id=?
                """, (rs, row) -> new FinancialTotals(rs.getLong("sent"), rs.getLong("paid"),
                rs.getLong("overdue"), rs.getLong("partial"), nullableDouble(rs, "avg_payment_hours")), organizationId);
        List<FinancialCurrencyResponse> currencies = jdbc.query("""
                SELECT currency,
                       count(*) FILTER (WHERE sent_at IS NOT NULL) AS sent,
                       count(*) FILTER (WHERE status IN ('PAID','REFUNDED','PARTIALLY_REFUNDED')) AS paid,
                       COALESCE(sum(total_minor) FILTER (WHERE sent_at IS NOT NULL),0) AS invoiced,
                       COALESCE(sum(amount_paid_minor),0) AS collected,
                       COALESCE(sum(amount_refunded_minor),0) AS refunded
                FROM client_onboarding.invoices
                WHERE organization_id=?
                GROUP BY currency
                ORDER BY currency
                """, (rs, row) -> new FinancialCurrencyResponse(rs.getString("currency"), rs.getLong("sent"),
                rs.getLong("paid"), rs.getLong("invoiced"), rs.getLong("collected"), rs.getLong("refunded")),
                organizationId);
        FinancialTotals safe = totals == null ? new FinancialTotals(0, 0, 0, 0, null) : totals;
        return new FinancialReportResponse(safe.sent(), safe.paid(), safe.overdue(), safe.partial(),
                safe.averagePaymentHours(), currencies);
    }

    @Transactional(readOnly = true)
    public ContractReportResponse contracts(UUID organizationId) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE sent_at IS NOT NULL) AS sent,
                       count(*) FILTER (WHERE status='SIGNED') AS signed,
                       count(*) FILTER (WHERE status='DECLINED') AS declined,
                       count(*) FILTER (WHERE status='EXPIRED') AS expired,
                       avg(EXTRACT(EPOCH FROM (signed_at-sent_at))/3600.0)
                         FILTER (WHERE signed_at IS NOT NULL AND sent_at IS NOT NULL) AS avg_signature_hours
                FROM client_onboarding.contracts WHERE organization_id=?
                """, (rs, row) -> {
            long sent = rs.getLong("sent");
            long signed = rs.getLong("signed");
            return new ContractReportResponse(sent, signed, rs.getLong("declined"), rs.getLong("expired"),
                    percent(signed, sent), nullableDouble(rs, "avg_signature_hours"));
        }, organizationId);
    }

    @Transactional(readOnly = true)
    public DurationReportResponse durations(UUID organizationId) {
        return jdbc.queryForObject("""
                WITH complete AS (
                    SELECT EXTRACT(EPOCH FROM (completed_at-started_at))/3600.0 AS hours
                    FROM client_onboarding.onboarding_instances
                    WHERE organization_id=? AND completed_at IS NOT NULL AND completed_at>=started_at
                ), step_events AS (
                    SELECT a.entity_id step_id, a.occurred_at, a.metadata->>'to' state,
                           lead(a.occurred_at) OVER (PARTITION BY a.entity_id ORDER BY a.occurred_at,a.id) next_at
                    FROM client_onboarding.activity_logs a
                    WHERE a.organization_id=? AND a.action='ONBOARDING_STEP_STATE_CHANGED'
                ), client_wait AS (
                    SELECT avg(EXTRACT(EPOCH FROM (COALESCE(e.next_at,CURRENT_TIMESTAMP)-e.occurred_at))/3600.0) AS hours
                    FROM step_events e JOIN client_onboarding.onboarding_step_instances s
                      ON s.organization_id=? AND s.id=e.step_id
                    WHERE s.client_visible=true AND e.state IN ('AVAILABLE','IN_PROGRESS','NEEDS_REVISION')
                ), review_wait AS (
                    SELECT avg(EXTRACT(EPOCH FROM (COALESCE(e.next_at,CURRENT_TIMESTAMP)-e.occurred_at))/3600.0) AS hours
                    FROM step_events e JOIN client_onboarding.onboarding_step_instances s
                      ON s.organization_id=? AND s.id=e.step_id
                    WHERE s.requires_review=true AND e.state IN ('SUBMITTED','UNDER_REVIEW')
                ), activation AS (
                    SELECT avg(EXTRACT(EPOCH FROM (a.occurred_at-o.completed_at))/3600.0) AS hours
                    FROM client_onboarding.activity_logs a
                    JOIN client_onboarding.onboarding_instances o
                      ON o.organization_id=a.organization_id AND o.project_id=a.project_id
                    WHERE a.organization_id=? AND a.action='PROJECT_ACTIVATED'
                      AND o.completed_at IS NOT NULL AND a.occurred_at>=o.completed_at
                )
                SELECT (SELECT avg(hours) FROM complete) avg_hours,
                       (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY hours) FROM complete) median_hours,
                       client_wait.hours client_wait_hours, review_wait.hours review_wait_hours,
                       activation.hours activation_hours
                FROM client_wait, review_wait, activation
                """, (rs, row) -> new DurationReportResponse(nullableDouble(rs, "avg_hours"),
                nullableDouble(rs, "median_hours"), nullableDouble(rs, "client_wait_hours"),
                nullableDouble(rs, "review_wait_hours"), nullableDouble(rs, "activation_hours")),
                organizationId, organizationId, organizationId, organizationId, organizationId);
    }

    @Transactional(readOnly = true)
    public PageSlice<OnboardingReportRow> onboardings(UUID organizationId, int page, int size, String status) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Page must be non-negative and size must be between 1 and 100.");
        }
        if (status != null && !status.isBlank() && !ONBOARDING_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ONBOARDING_STATUS", "Unknown onboarding status filter.");
        }
        String statusFilter = status == null || status.isBlank() ? "" : " AND o.status=? ";
        Object[] countArgs = statusFilter.isEmpty() ? new Object[]{organizationId} : new Object[]{organizationId, status};
        Long total = jdbc.queryForObject("SELECT count(*) FROM client_onboarding.onboarding_instances o WHERE o.organization_id=?" + statusFilter,
                Long.class, countArgs);
        String sql = """
                SELECT o.id onboarding_id, o.project_id, p.name project_name, p.client_id, c.name client_name,
                       o.status onboarding_status, p.status project_status, o.started_at, o.completed_at,
                       COALESCE(progress.percent,0) progress_percent, COALESCE(overdue.value,0) overdue_steps
                FROM client_onboarding.onboarding_instances o
                JOIN client_onboarding.projects p ON p.organization_id=o.organization_id AND p.id=o.project_id
                JOIN client_onboarding.clients c ON c.organization_id=p.organization_id AND c.id=p.client_id
                LEFT JOIN LATERAL (
                    SELECT CASE WHEN count(*)=0 THEN 100 ELSE floor(100.0*count(*) FILTER (WHERE s.status IN ('COMPLETED','SKIPPED'))/count(*))::int END percent
                    FROM client_onboarding.onboarding_step_instances s
                    WHERE s.organization_id=o.organization_id AND s.onboarding_id=o.id
                      AND (s.required=true OR s.blocking=true OR s.step_type NOT IN ('WELCOME','INSTRUCTION','EXTERNAL_LINK','VIDEO_GUIDE'))
                ) progress ON true
                LEFT JOIN LATERAL (
                    SELECT count(*) value FROM client_onboarding.onboarding_step_instances s
                    WHERE s.organization_id=o.organization_id AND s.onboarding_id=o.id AND s.due_at<CURRENT_TIMESTAMP
                      AND s.status NOT IN ('COMPLETED','SKIPPED','CANCELLED')
                      AND o.status NOT IN ('COMPLETED','CANCELLED','EXPIRED','PAUSED')
                ) overdue ON true
                WHERE o.organization_id=?
                """ + statusFilter + " ORDER BY o.started_at DESC,o.id DESC LIMIT ? OFFSET ?";
        Object[] args = statusFilter.isEmpty()
                ? new Object[]{organizationId, size, Math.multiplyExact((long) page, (long) size)}
                : new Object[]{organizationId, status, size, Math.multiplyExact((long) page, (long) size)};
        List<OnboardingReportRow> rows = jdbc.query(sql, (rs, row) -> new OnboardingReportRow(
                rs.getObject("onboarding_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("project_name"),
                rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getString("onboarding_status"),
                rs.getString("project_status"), rs.getInt("progress_percent"), rs.getLong("overdue_steps"),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()), args);
        long count = total == null ? 0 : total;
        return new PageSlice<>(rows, page, size, count, (count + size - 1) / size);
    }

    private static double percent(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round((10000.0 * numerator / denominator)) / 100.0;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : Math.round(value * 100.0) / 100.0;
    }

    private record FinancialTotals(long sent, long paid, long overdue, long partial, Double averagePaymentHours) {}

    public record PageSlice<T>(List<T> items, int page, int size, long totalElements, long totalPages) {}
}
