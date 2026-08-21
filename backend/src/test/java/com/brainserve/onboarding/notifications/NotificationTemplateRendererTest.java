package com.brainserve.onboarding.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.notifications.application.service.NotificationTemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateRendererTest {
    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test void rendersOnlyAllowlistedVariablesAndFlattensHeaderBreakingNewlines() {
        renderer.validate("Project {{project_id}}", "Hello {{recipient_name}}", "/app/projects/{{project_id}}");
        assertThat(renderer.render("Hello {{recipient_name}}", Map.of("recipient_name", "A\r\nB"))).isEqualTo("Hello A  B");
    }

    @Test void rejectsUnsupportedVariablesAndUnsafeActionPaths() {
        assertThatThrownBy(() -> renderer.validate("{{password}}", "Body", "/app"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> renderer.validate("Subject", "Body", "https://evil.example"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> renderer.validate("Subject", "Body", "/portal//evil"))
                .isInstanceOf(ApiException.class);
    }
}
