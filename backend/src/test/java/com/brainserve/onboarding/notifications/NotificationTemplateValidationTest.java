package com.brainserve.onboarding.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.notifications.api.request.CreateNotificationTemplateRequest;
import com.brainserve.onboarding.notifications.application.service.NotificationTemplateRenderer;
import com.brainserve.onboarding.notifications.application.service.NotificationTemplateService;
import com.brainserve.onboarding.notifications.infrastructure.persistence.NotificationTemplateRepository;
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

class NotificationTemplateValidationTest {
    @Test
    void permissionRecipientWithoutPermissionIsReportedAsControlledBadRequest() {
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        when(templates.existsByOrganizationIdAndCode(org(), "PROJECT_READY")).thenReturn(false);
        NotificationTemplateService service = new NotificationTemplateService(
                templates,
                new NotificationTemplateRenderer(),
                new ObjectMapper(),
                mock(AuditService.class),
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));

        var request = new CreateNotificationTemplateRequest(
                "PROJECT_READY", "Project ready", "PROJECT_READY", "PERMISSION", null,
                "Project ready", "Project {{project_id}} is ready.", "/app/projects/{{project_id}}",
                List.of("IN_APP"), false);

        assertThatThrownBy(() -> service.create(principal(), request, mock(HttpServletRequest.class)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.code()).isEqualTo("NOTIFICATION_TEMPLATE_INVALID");
                });
    }

    private static final UUID ORG = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static UUID org() { return ORG; }
    private static TenantPrincipal principal() {
        return new TenantPrincipal(UUID.randomUUID(), ORG, UUID.randomUUID(), UUID.randomUUID(),
                "admin@example.com", "Admin", "Example", "example", Set.of("NOTIFICATION_MANAGE"));
    }
}
