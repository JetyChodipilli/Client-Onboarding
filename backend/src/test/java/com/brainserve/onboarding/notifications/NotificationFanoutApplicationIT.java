package com.brainserve.onboarding.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.notifications.application.service.NotificationService;
import com.brainserve.onboarding.notifications.domain.model.NotificationChannel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class NotificationFanoutApplicationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("client_onboarding")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.require-explicit-secrets", () -> "false");
        registry.add("app.security.secure-cookies", () -> "false");
    }

    @Autowired NotificationService notifications;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseDedupeAndResolvedPreferencesKeepFanoutIdempotent() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        String suffix = organizationId.toString().substring(0, 8);
        String email = "fanout-" + suffix + "@example.com";

        jdbc.update(
                "INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)",
                organizationId,
                "Fanout Test " + suffix,
                "fanout-" + suffix);
        jdbc.update(
                """
                INSERT INTO client_onboarding.users
                  (id,email,normalized_email,display_name,password_hash,email_verified_at)
                VALUES (?,?,?,?,?,?)
                """,
                userId,
                email,
                email,
                "Fanout User",
                "test-only-password-hash",
                Instant.now());
        jdbc.update(
                """
                INSERT INTO client_onboarding.organization_users
                  (id,organization_id,user_id,status,joined_at)
                VALUES (?,?,?,'ACTIVE',?)
                """,
                membershipId,
                organizationId,
                userId,
                Instant.now());

        String dedupeKey = "fanout-test:" + UUID.randomUUID();
        boolean created = notifications.createForResolvedRecipient(
                organizationId,
                userId,
                "INTERNAL",
                email,
                true,
                false,
                null,
                "TEST_EVENT",
                "TEST_SOURCE",
                UUID.randomUUID(),
                null,
                "Test notification",
                "Body",
                "/app",
                dedupeKey,
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                false);
        boolean duplicate = notifications.createForResolvedRecipient(
                organizationId,
                userId,
                "INTERNAL",
                email,
                true,
                false,
                null,
                "TEST_EVENT",
                "TEST_SOURCE",
                UUID.randomUUID(),
                null,
                "Duplicate must not be inserted",
                "Body",
                "/app",
                dedupeKey,
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                false);

        assertThat(created).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM client_onboarding.notifications WHERE organization_id=? AND recipient_user_id=? AND dedupe_key=?",
                Long.class,
                organizationId,
                userId,
                dedupeKey)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM client_onboarding.notification_deliveries d
                JOIN client_onboarding.notifications n ON n.id=d.notification_id
                WHERE n.organization_id=? AND n.recipient_user_id=? AND n.dedupe_key=?
                """,
                Long.class,
                organizationId,
                userId,
                dedupeKey)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM client_onboarding.notification_deliveries d
                JOIN client_onboarding.notifications n ON n.id=d.notification_id
                WHERE n.organization_id=? AND n.recipient_user_id=? AND n.dedupe_key=?
                  AND d.channel='EMAIL' AND d.status='SUPPRESSED'
                """,
                Long.class,
                organizationId,
                userId,
                dedupeKey)).isEqualTo(1L);
    }
}
