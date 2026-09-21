package com.brainserve.clientonboarding.auth.api;

import com.brainserve.clientonboarding.auth.application.SecurityNotificationPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class TestSecurityNotificationConfiguration {
    @Bean
    @Primary
    SecurityNotificationPort testSecurityNotifications() {
        return new SecurityNotificationPort() {
            @Override public void sendVerification(String recipient, String displayName, String verificationUrl) { }
            @Override public void sendPasswordReset(String recipient, String displayName, String resetUrl) { }
            @Override public void sendOrganizationInvitation(String recipient, String displayName,
                                                              String organizationName, String invitationUrl) { }
        };
    }
}
