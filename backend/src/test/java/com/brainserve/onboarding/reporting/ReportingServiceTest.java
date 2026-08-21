package com.brainserve.onboarding.reporting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.reporting.application.service.ReportingService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportingServiceTest {
    private final ReportingService service = new ReportingService(
            mock(JdbcTemplate.class), Clock.fixed(Instant.parse("2026-08-21T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void rejectsUnboundedOrNegativePaginationBeforeQueryingTheDatabase() {
        UUID organizationId = UUID.randomUUID();
        assertThatThrownBy(() -> service.onboardings(organizationId, -1, 25, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Page must be non-negative");
        assertThatThrownBy(() -> service.onboardings(organizationId, 0, 101, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("between 1 and 100");
    }

    @Test
    void rejectsUnknownStatusInsteadOfInterpolatingItIntoSql() {
        assertThatThrownBy(() -> service.onboardings(UUID.randomUUID(), 0, 25, "COMPLETED' OR true --"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown onboarding status");
    }
}
