package com.brainserve.clientonboarding.auth.api;

import com.brainserve.clientonboarding.auth.application.SecurityNotificationPort;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityNotificationConfiguration {
    @Bean
    @Primary
    TestNotificationSender testSecurityNotifications() {
        return new TestNotificationSender();
    }

    public static final class TestNotificationSender implements SecurityNotificationPort {
        private final AtomicBoolean failNextClientInvitation = new AtomicBoolean();

        public void failNextClientInvitation() {
            failNextClientInvitation.set(true);
        }

        public void reset() {
            failNextClientInvitation.set(false);
        }

        @Override public void sendVerification(String recipient, String displayName, String verificationUrl) { }
        @Override public void sendPasswordReset(String recipient, String displayName, String resetUrl) { }
        @Override public void sendOrganizationInvitation(String recipient, String displayName,
                                                          String organizationName, String invitationUrl) { }
        @Override public void sendClientInvitation(String recipient, String displayName,
                                                    String organizationName, String projectName,
                                                    String invitationUrl) {
            if (failNextClientInvitation.getAndSet(false)) {
                throw new IllegalStateException("simulated invitation delivery failure");
            }
        }
    }
}
