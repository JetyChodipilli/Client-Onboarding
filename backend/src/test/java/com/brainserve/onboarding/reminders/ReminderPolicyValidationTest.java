package com.brainserve.onboarding.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.reminders.api.request.CreateReminderPolicyRequest;
import com.brainserve.onboarding.reminders.application.service.ReminderPolicyService;
import com.brainserve.onboarding.reminders.infrastructure.persistence.ReminderPolicyRepository;
import com.brainserve.onboarding.reminders.infrastructure.persistence.ScheduledReminderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReminderPolicyValidationTest {
    @Test
    void invalidTimezoneIsReportedAsControlledBadRequest() {
        ReminderPolicyRepository policies = mock(ReminderPolicyRepository.class);
        when(policies.existsByOrganizationIdAndNameIgnoreCase(org(), "Follow up")).thenReturn(false);
        ReminderPolicyService service = new ReminderPolicyService(
                policies,
                mock(ScheduledReminderRepository.class),
                new ObjectMapper(),
                mock(AuditService.class),
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));

        var request = new CreateReminderPolicyRequest(
                "Follow up", null, 60, 1440, 3, true, "Not/A_Real_Zone", List.of("EMAIL"), true);

        assertThatThrownBy(() -> service.create(principal(), request, mock(HttpServletRequest.class)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.code()).isEqualTo("REMINDER_POLICY_INVALID");
                });
    }

    private static final UUID ORG = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static UUID org() { return ORG; }
    private static TenantPrincipal principal() {
        return new TenantPrincipal(UUID.randomUUID(), ORG, UUID.randomUUID(), UUID.randomUUID(),
                "admin@example.com", "Admin", "Example", "example", Set.of("REMINDER_MANAGE"));
    }
}
