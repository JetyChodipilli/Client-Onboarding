package com.brainserve.clientonboarding.portal.api;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainserve.clientonboarding.auth.api.TestSecurityNotificationConfiguration;
import com.brainserve.clientonboarding.auth.api.TestSecurityNotificationConfiguration.TestNotificationSender;
import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityNotificationConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase4IntegrationTest {
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").start(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres?currentSchema=app");
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void closePostgres() throws java.io.IOException { POSTGRES.close(); }
    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthRepository auth;
    @Autowired com.brainserve.clientonboarding.auth.application.AuthService authService;
    @Autowired SecureTokenService tokens;
    @Autowired Clock clock;
    @Autowired DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.brainserve.clientonboarding.onboarding.domain.repository.OnboardingRepository onboardings;
    @Autowired TestNotificationSender notifications;

    private UUID organizationA;
    private UUID organizationB;
    private UUID onboardingId;
    private UUID projectId;
    private UUID contactId;
    private Cookie managerA;
    private Cookie managerB;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(dataSource).schemas("app").defaultSchema("app")
                .createSchemas(true).locations("classpath:db/migration").load().migrate();
        clearData();
        notifications.reset();
        organizationA = organization("portal-a", "Portal Organization A");
        organizationB = organization("portal-b", "Portal Organization B");
        managerA = internalSession(organizationA, "manager-portal-a@example.test",
                Set.of("ONBOARDING_INVITE", "WORKFLOW_READ", "ONBOARDING_START", "PROJECT_UPDATE", "PROJECT_READ", "CLIENT_READ", "SERVICE_MANAGE", "AUDIT_READ"));
        managerB = internalSession(organizationB, "manager-portal-b@example.test",
                Set.of("ONBOARDING_INVITE", "WORKFLOW_READ", "ONBOARDING_START", "PROJECT_UPDATE", "PROJECT_READ", "CLIENT_READ", "SERVICE_MANAGE", "AUDIT_READ"));
        seedOnboarding();
    }

    @Test
    void invitationAcceptanceCreatesProjectScopedClientAccessAndHidesInternalSteps() throws Exception {
        MvcResult invited = mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(managerA).with(csrf()).header("Idempotency-Key", "phase4-invite-0001")
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("contactId", contactId, "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("SENT"))
                .andReturn();
        UUID invitationId = UUID.fromString(tree(invited).at("/data/id").asText());

        mockMvc.perform(get("/api/v1/onboardings/{id}/client-invitations", onboardingId).cookie(managerB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/client-invitations/{id}/resend", invitationId)
                        .cookie(managerB).with(csrf()).contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isNotFound());

        String rawToken = "phase4-client-invitation-token-with-enough-entropy-0001";
        jdbc.sql("UPDATE client_invitations SET token_hash = :hash WHERE id = :id")
                .param("hash", tokens.hash(rawToken)).param("id", invitationId).update();
        mockMvc.perform(post("/api/v1/client-invitations/inspect").with(csrf())
                        .contentType("application/json").content(tokenBody(rawToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.projectName").value("Portal Launch"));

        String password = "ClientPortal7Password";
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", rawToken, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationSlug").value("portal-a"));
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", rawToken, "password", password))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INVALID_INVITATION"));

        MvcResult loggedIn = mockMvc.perform(post("/api/v1/client-auth/login").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("email", "client@portal.test",
                                "password", password, "organizationSlug", "portal-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").exists())
                .andReturn();
        Cookie clientCookie = sessionCookie(loggedIn);

        mockMvc.perform(get("/api/v1/client-portal/projects").cookie(clientCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$.data[0].progress").value(0));
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(clientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data.progress").value(0))
                .andExpect(jsonPath("$.data.steps", hasSize(2)))
                .andExpect(jsonPath("$.data.steps[0].name").value("Welcome to the project"));
        mockMvc.perform(get("/api/v1/onboardings/{id}", onboardingId).cookie(clientCookie))
                .andExpect(status().isForbidden());

        UUID visibleStep = jdbc.sql("SELECT id FROM onboarding_step_instances WHERE onboarding_id = :id AND step_key = 'WELCOME'")
                .param("id", onboardingId).query(UUID.class).single();
        mockMvc.perform(post("/api/v1/client-portal/projects/{projectId}/steps/{stepId}/transition",
                        projectId, visibleStep).cookie(clientCookie).with(csrf()).contentType("application/json")
                        .content("{\"targetStatus\":\"IN_PROGRESS\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].status").value("IN_PROGRESS"));
    }

    @Test
    void expiredAndRevokedInvitationsCannotActivateAccounts() throws Exception {
        UUID expired = directInvitation("expired-token", Instant.now().minusSeconds(5));
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", "expired-token",
                                "password", "ClientPortal7Password"))))
                .andExpect(status().isGone());
        jdbc.sql("UPDATE client_invitations SET expires_at = :future WHERE id = :id")
                .param("future", timestamp(Instant.now().plusSeconds(3600))).param("id", expired).update();
        mockMvc.perform(post("/api/v1/client-invitations/{id}/revoke", expired).cookie(managerA).with(csrf())
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(post("/api/v1/client-invitations/inspect").with(csrf())
                        .contentType("application/json").content(tokenBody("expired-token")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void invitationDeliveryFailureDoesNotCorruptAuthoritativeState() throws Exception {
        notifications.failNextClientInvitation();
        mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(managerA).with(csrf()).header("Idempotency-Key", "phase4-mail-failure-0001")
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("contactId", contactId, "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("FAILED"));
        mockMvc.perform(get("/api/v1/onboardings/{id}", onboardingId).cookie(managerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.status").value("INVITED"));
    }

    @Test
    void clientMembersSeeOnlyGenerallyAssignedOrMemberSteps() throws Exception {
        MvcResult invited = mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(managerA).with(csrf()).header("Idempotency-Key", "phase4-member-role-0001")
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("contactId", contactId, "role", "MEMBER"))))
                .andExpect(status().isCreated()).andReturn();
        UUID invitationId = UUID.fromString(tree(invited).at("/data/id").asText());
        String rawToken = "phase4-member-invitation-token-with-enough-entropy";
        jdbc.sql("UPDATE client_invitations SET token_hash = :hash WHERE id = :id")
                .param("hash", tokens.hash(rawToken)).param("id", invitationId).update();
        String password = "ClientPortal7Password";
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", rawToken, "password", password))))
                .andExpect(status().isOk());
        MvcResult loggedIn = mockMvc.perform(post("/api/v1/client-auth/login").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("email", "client@portal.test",
                                "password", password, "organizationSlug", "portal-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andReturn();
        Cookie clientCookie = sessionCookie(loggedIn);
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(clientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps", hasSize(1)))
                .andExpect(jsonPath("$.data.steps[0].name").value("Welcome to the project"));
        notifications.failNextPasswordReset();
        mockMvc.perform(post("/api/v1/client-auth/forgot-password").with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("email", "client@portal.test",
                                "organizationSlug", "portal-a"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.message").value(
                        "If the account is eligible, a reset link has been sent."));
    }

    @Test
    void invitationAuthorizationRotationAndOptimisticConflictsAreEnforced() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("contactId", contactId, "role", "MEMBER"));
        mockMvc.perform(get("/api/v1/onboardings/{id}/client-invitations", onboardingId))
                .andExpect(status().isUnauthorized());
        Cookie reader = internalSession(organizationA, "reader@portal.test", Set.of("WORKFLOW_READ"));
        mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(reader).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(managerA).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        MvcResult first = mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(managerA).with(csrf()).header("Idempotency-Key", "rotation-test-0001")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        String id = tree(first).at("/data/id").asText();
        long version = tree(first).at("/data/version").asLong();
        mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId)
                        .cookie(managerA).with(csrf()).header("Idempotency-Key", "rotation-test-0001")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        String oldToken = "rotation-test-old-token";
        jdbc.sql("UPDATE client_invitations SET token_hash = :hash WHERE id = :id")
                .param("hash", tokens.hash(oldToken)).param("id", UUID.fromString(id)).update();
        MvcResult rotated = mockMvc.perform(post("/api/v1/client-invitations/{id}/resend", id)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.resendCount").value(1)).andReturn();
        mockMvc.perform(post("/api/v1/client-invitations/inspect").with(csrf())
                        .contentType("application/json").content(tokenBody(oldToken)))
                .andExpect(status().isGone());
        mockMvc.perform(post("/api/v1/client-invitations/{id}/revoke", id)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/client-invitations/{id}/revoke", id)
                        .cookie(managerB).with(csrf()).contentType("application/json")
                        .content("{\"version\":" + tree(rotated).at("/data/version").asLong() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/onboardings/{id}/client-invitations?size=101", onboardingId).cookie(managerA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientCannotBypassProjectGrantRoleReviewOrPausedState() throws Exception {
        Cookie client = activateMember();
        UUID welcome = step("WELCOME");
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", UUID.randomUUID()).cookie(client))
                .andExpect(status().isNotFound());
        // A real project in the same tenant still requires an explicit project grant.
        UUID ungranted = UUID.randomUUID();
        jdbc.sql("INSERT INTO projects (id, organization_id, client_id, service_id, name, status, created_at, created_by, updated_at, updated_by, version) SELECT :id,organization_id,client_id,service_id,'Not shared','DRAFT',created_at,created_by,updated_at,updated_by,0 FROM projects WHERE id=:source")
                .param("id", ungranted).param("source", projectId).update();
        jdbc.sql("INSERT INTO onboarding_instances (id, organization_id, project_id, source_template_id, source_template_version_id, snapshot_version_number, snapshot_json, status, ready, started_at, created_at, created_by, updated_at, updated_by, version) SELECT :id, organization_id, :project, source_template_id, source_template_version_id, snapshot_version_number, snapshot_json, status, ready, started_at, created_at, created_by, updated_at, updated_by, 0 FROM onboarding_instances WHERE id=:source")
                .param("id", UUID.randomUUID()).param("project", ungranted).param("source", onboardingId).update();
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", ungranted).cookie(client))
                .andExpect(status().isNotFound());
        transition(client, step("ADMIN_GUIDE"), "IN_PROGRESS", 0).andExpect(status().isNotFound());
        transition(client, step("INTERNAL"), "IN_PROGRESS", 0).andExpect(status().isNotFound());
        jdbc.sql("UPDATE onboarding_step_instances SET requires_review=TRUE WHERE id=:id")
                .param("id", welcome).update();
        transition(client, welcome, "IN_PROGRESS", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextAction.requiresReview").value(true));
        transition(client, welcome, "COMPLETED", 1).andExpect(status().isConflict());
        transition(client, welcome, "SUBMITTED", 0).andExpect(status().isConflict());
        for (String state : List.of("PAUSED", "EXPIRED", "CANCELLED", "COMPLETED")) {
            jdbc.sql("UPDATE onboarding_instances SET status=:status WHERE id=:id")
                    .param("status", state).param("id", onboardingId).update();
            transition(client, welcome, "SUBMITTED", 1).andExpect(status().isConflict());
            mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStatus").value(state))
                    .andExpect(jsonPath("$.data.steps[0].actionable").value(false));
        }
        jdbc.sql("UPDATE onboarding_instances SET status='IN_PROGRESS' WHERE id=:id").param("id", onboardingId).update();
        transition(client, welcome, "SUBMITTED", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.waitingFor").value("OUR_TEAM"));
        jdbc.sql("UPDATE onboarding_step_instances SET status='NEEDS_REVISION' WHERE id=:id").param("id", welcome).update();
        transition(client, welcome, "IN_PROGRESS", 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextAction.actionable").value(true));
        mockMvc.perform(get("/api/v1/client-portal/projects?size=101").cookie(client)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/client-portal/projects").cookie(managerA)).andExpect(status().isForbidden());
    }

    @Test
    void pausedOnboardingRejectsInvitationAcceptanceAndResend() throws Exception {
        UUID invitation = directInvitation("paused-invitation", Instant.now().plusSeconds(3600));
        jdbc.sql("UPDATE onboarding_instances SET status='PAUSED' WHERE id=:id").param("id", onboardingId).update();
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", "paused-invitation", "password", "ClientPortal7Password"))))
                .andExpect(status().isGone());
        mockMvc.perform(post("/api/v1/client-invitations/{id}/resend", invitation).cookie(managerA).with(csrf())
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void projectHoldResumeAndCancellationControlBothStepEntryPoints() throws Exception {
        Cookie client = activateMember();
        UUID welcome = step("WELCOME");
        projectAction("hold", 1).andExpect(status().isOk());
        transition(client, welcome, "IN_PROGRESS", 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_ONBOARDING"));
        internalTransition(welcome).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStatus").value("ON_HOLD"))
                .andExpect(jsonPath("$.data.nextAction").doesNotExist())
                .andExpect(jsonPath("$.data.steps[0].actionable").value(false))
                .andExpect(jsonPath("$.data.steps[0].waitingFor").value("OUR_TEAM"));
        projectAction("resume", 2).andExpect(status().isOk());
        for (String state : List.of("PAUSED", "EXPIRED", "CANCELLED", "APPROVED", "COMPLETED")) {
            jdbc.sql("UPDATE onboarding_instances SET status=:status WHERE id=:id")
                    .param("status", state).param("id", onboardingId).update();
            internalTransition(welcome).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ONBOARDING_NOT_ACTIVE"));
        }
        jdbc.sql("UPDATE onboarding_instances SET status='IN_PROGRESS' WHERE id=:id").param("id", onboardingId).update();
        transition(client, welcome, "IN_PROGRESS", 0).andExpect(status().isOk());
        projectAction("cancel", 3).andExpect(status().isOk());
        transition(client, welcome, "COMPLETED", 1).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStatus").value("CANCELLED"));
        projectAction("archive", 4).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentProjectHoldCommitsBeforeClientStepAuthorization() throws Exception {
        Cookie client = activateMember();
        UUID welcome = step("WELCOME");
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var update = connection.prepareStatement("UPDATE projects SET status='ON_HOLD', previous_status='ONBOARDING' WHERE id=?")) {
                update.setObject(1, projectId);
                update.executeUpdate();
            }
            var attempt = executor.submit(() -> transition(client, welcome, "IN_PROGRESS", 0));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> attempt.get(250, java.util.concurrent.TimeUnit.MILLISECONDS));
            connection.commit();
            attempt.get(10, java.util.concurrent.TimeUnit.SECONDS).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_ONBOARDING"));
            org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", jdbc.sql(
                    "SELECT status FROM onboarding_step_instances WHERE id=:id").param("id", welcome).query(String.class).single());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void heldProjectRejectsNewInvitationsResendAndAcceptance() throws Exception {
        UUID invitation = directInvitation("held-invitation", Instant.now().plusSeconds(3600));
        projectAction("hold", 1).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/onboardings/{id}/client-invitations", onboardingId).cookie(managerA)
                        .with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("contactId", contactId, "role", "MEMBER"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/client-invitations/{id}/resend", invitation).cookie(managerA)
                        .with(csrf()).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", "held-invitation", "password", "ClientPortal7Password"))))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", jdbc.sql(
                "SELECT status FROM client_invitations WHERE id=:id").param("id", invitation).query(String.class).single());
    }

    @Test
    void lockedStepsWaitingOnReviewNeverAssignCompletedClientWorkBackToClient() throws Exception {
        Cookie client = activateMember();
        UUID welcome = step("WELCOME"), dependent = step("ADMIN_GUIDE");
        jdbc.sql("UPDATE onboarding_step_instances SET assigned_role=NULL,status='LOCKED',dependency_mode='ALL' WHERE id=:id")
                .param("id", dependent).update();
        jdbc.sql("INSERT INTO onboarding_step_instance_dependencies (organization_id,onboarding_id,step_instance_id,depends_on_step_instance_id) VALUES (:org,:onboarding,:step,:dependency)")
                .param("org", organizationA).param("onboarding", onboardingId).param("step", dependent).param("dependency", welcome).update();
        for (String mode : List.of("ALL", "ANY")) {
            jdbc.sql("UPDATE onboarding_step_instances SET dependency_mode=:mode WHERE id=:id").param("mode", mode).param("id", dependent).update();
            for (String state : List.of("SUBMITTED", "UNDER_REVIEW")) {
                jdbc.sql("UPDATE onboarding_step_instances SET requires_review=TRUE,status=:status WHERE id=:id")
                        .param("status", state).param("id", welcome).update();
                mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextAction").doesNotExist())
                        .andExpect(jsonPath("$.data.steps[1].waitingFor").value("OUR_TEAM"))
                        .andExpect(jsonPath("$.data.steps[1].blockingReason").value("Your team is reviewing a prerequisite."));
            }
        }
        jdbc.sql("UPDATE onboarding_step_instances SET status='NEEDS_REVISION' WHERE id=:id").param("id", welcome).update();
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextAction.id").value(welcome.toString()))
                .andExpect(jsonPath("$.data.steps[1].waitingFor").value("YOUR_ACTION"));
        jdbc.sql("UPDATE onboarding_step_instances SET client_visible=FALSE WHERE id=:id").param("id", welcome).update();
        mockMvc.perform(get("/api/v1/client-portal/projects/{id}", projectId).cookie(client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.steps", hasSize(1)))
                .andExpect(jsonPath("$.data.steps[0].waitingFor").value("OUR_TEAM"))
                .andExpect(jsonPath("$.data.steps[0].blockingReason").value("Your team is completing a prerequisite."));
    }

    @Test
    void malformedUrlInputsAreClientErrorsAndLargePagesDoNotOverflow() throws Exception {
        mockMvc.perform(get("/api/v1/onboardings/not-a-uuid").cookie(managerA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/projects?size=not-a-number").cookie(managerA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/projects/{id}/hold", projectId).cookie(managerA).with(csrf()))
                .andExpect(status().isBadRequest());
        for (String endpoint : List.of("clients", "projects", "services", "workflow-templates", "audit-logs")) {
            mockMvc.perform(get("/api/v1/" + endpoint + "?page=2147483647&size=100").cookie(managerA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    private org.springframework.test.web.servlet.ResultActions projectAction(String action, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{id}/" + action, projectId).param("version", Long.toString(version))
                .cookie(managerA).with(csrf()));
    }

    private org.springframework.test.web.servlet.ResultActions internalTransition(UUID step) throws Exception {
        return mockMvc.perform(post("/api/v1/onboarding-steps/{id}/transition", step).cookie(managerA).with(csrf())
                .contentType("application/json").content("{\"targetStatus\":\"IN_PROGRESS\",\"version\":0}"));
    }

    @Test
    void clientSessionRotationRejectsPreviouslyAuthenticatedReplayAndRevocation() throws Exception {
        Cookie client = activateMember();
        var original = auth.findSessionByTokenHash(tokens.hash(client.getValue())).orElseThrow();
        UUID membership = jdbc.sql("SELECT id FROM client_users WHERE organization_id=:org AND user_id=:user")
                .param("org", organizationA).param("user", original.userId()).query(UUID.class).single();
        var principal = new com.brainserve.clientonboarding.common.security.TenantPrincipal(original.userId(),
                organizationA, "Portal Organization A", "portal-a", membership, "client@portal.test", "Client",
                "CLIENT_MEMBER", Set.of("CLIENT_PORTAL_READ"), original.id(), false);
        var metadata = new com.brainserve.clientonboarding.common.observability.RequestMetadata("ip", "agent");
        var rotated = authService.refresh(principal, metadata);
        mockMvc.perform(get("/api/v1/client-portal/projects").cookie(client)).andExpect(status().isUnauthorized());
        // Simulate a second request authenticated before the first rotation committed.
        var replay = org.junit.jupiter.api.Assertions.assertThrows(
                com.brainserve.clientonboarding.common.error.DomainException.class,
                () -> authService.refresh(principal, metadata));
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, replay.status());
        var next = new Cookie("BOS_SESSION", rotated.rawToken());
        mockMvc.perform(get("/api/v1/client-portal/projects").cookie(next)).andExpect(status().isOk());
        var nextPrincipal = new com.brainserve.clientonboarding.common.security.TenantPrincipal(original.userId(),
                organizationA, "Portal Organization A", "portal-a", membership, "client@portal.test", "Client",
                "CLIENT_MEMBER", Set.of("CLIENT_PORTAL_READ"), rotated.sessionId(), false);
        auth.revokeAllSessions(original.userId(), clock.instant());
        org.junit.jupiter.api.Assertions.assertThrows(com.brainserve.clientonboarding.common.error.DomainException.class,
                () -> authService.refresh(nextPrincipal, metadata));
    }

    @Test
    void concurrentRoleEditsCannotRemoveEveryManager() throws Exception {
        Cookie first = internalSession(organizationA, "first-admin@portal.test", Set.of("USER_MANAGE", "ROLE_MANAGE"));
        Cookie second = internalSession(organizationA, "second-admin@portal.test", Set.of("USER_MANAGE", "ROLE_MANAGE"));
        UUID firstRole = jdbc.sql("SELECT ou.role_id FROM organization_users ou JOIN users u ON u.id=ou.user_id WHERE u.email='first-admin@portal.test'").query(UUID.class).single();
        UUID secondRole = jdbc.sql("SELECT ou.role_id FROM organization_users ou JOIN users u ON u.id=ou.user_id WHERE u.email='second-admin@portal.test'").query(UUID.class).single();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var one = executor.submit(() -> { start.await(); return removeManagerPermissions(first, firstRole, "First"); });
            var two = executor.submit(() -> { start.await(); return removeManagerPermissions(second, secondRole, "Second"); });
            start.countDown();
            var responses = List.of(one.get(10, java.util.concurrent.TimeUnit.SECONDS), two.get(10, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(List.of(200, 409), responses.stream()
                    .map(result -> result.getResponse().getStatus()).sorted().toList());
            var rejected = responses.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("LAST_MANAGER_REQUIRED", tree(rejected).at("/error/code").asText());
        } finally { executor.shutdownNow(); }
    }

    private MvcResult removeManagerPermissions(Cookie session, UUID role, String name) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/roles/{id}", role)
                .cookie(session).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("name", name, "description", "", "permissions", List.of(), "version", 0))))
                .andReturn();
    }

    @Test
    void tenantCommandLockDoesNotBlockUnrelatedStepAuditForeignKeys() throws Exception {
        Cookie client = activateMember();
        UUID welcome = step("WELCOME");
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                onboardings.lockTenantCommands(organizationA);
                var attempt = executor.submit(() -> transition(client, welcome, "IN_PROGRESS", 0));
                try { attempt.get(5, java.util.concurrent.TimeUnit.SECONDS).andExpect(status().isOk()); }
                catch (Exception failure) { throw new AssertionError("Tenant command locking must allow audit foreign-key reads", failure); }
            });
        } finally { executor.shutdownNow(); }
    }

    private UUID step(String key) {
        return jdbc.sql("SELECT id FROM onboarding_step_instances WHERE onboarding_id=:id AND step_key=:key")
                .param("id", onboardingId).param("key", key).query(UUID.class).single();
    }

    private org.springframework.test.web.servlet.ResultActions transition(Cookie client, UUID step, String status, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/client-portal/projects/{project}/steps/{step}/transition", projectId, step)
                .cookie(client).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("targetStatus", status, "version", version))));
    }

    private Cookie activateMember() throws Exception {
        directInvitation("member-test-token", Instant.now().plusSeconds(3600));
        jdbc.sql("UPDATE onboarding_instances SET status='INVITED' WHERE id=:id").param("id", onboardingId).update();
        mockMvc.perform(post("/api/v1/client-invitations/accept").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("token", "member-test-token", "password", "ClientPortal7Password"))))
                .andExpect(status().isOk());
        return sessionCookie(mockMvc.perform(post("/api/v1/client-auth/login").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("email", "client@portal.test", "password", "ClientPortal7Password", "organizationSlug", "portal-a"))))
                .andExpect(status().isOk()).andReturn());
    }

    private void seedOnboarding() {
        Instant now = Instant.now();
        UUID actor = jdbc.sql("SELECT user_id FROM organization_users WHERE organization_id = :org")
                .param("org", organizationA).query(UUID.class).single();
        UUID clientId = UUID.randomUUID(); contactId = UUID.randomUUID(); UUID serviceId = UUID.randomUUID();
        projectId = UUID.randomUUID(); UUID templateId = UUID.randomUUID(); UUID versionId = UUID.randomUUID();
        UUID visibleSource = UUID.randomUUID(); UUID hiddenSource = UUID.randomUUID(); onboardingId = UUID.randomUUID();
        jdbc.sql("INSERT INTO clients (id, organization_id, name, status, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,'Portal Client','ACTIVE',:now,:actor,:now,:actor,0)")
                .param("id", clientId).param("org", organizationA).param("now", timestamp(now)).param("actor", actor).update();
        jdbc.sql("INSERT INTO client_contacts (id, organization_id, client_id, name, email, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:client,'Client Contact','client@portal.test',:now,:actor,:now,:actor,0)")
                .param("id", contactId).param("org", organizationA).param("client", clientId).param("now", timestamp(now)).param("actor", actor).update();
        jdbc.sql("INSERT INTO services (id, organization_id, code, name, status, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,'PORTAL','Portal Service','ACTIVE',:now,:actor,:now,:actor,0)")
                .param("id", serviceId).param("org", organizationA).param("now", timestamp(now)).param("actor", actor).update();
        jdbc.sql("INSERT INTO projects (id, organization_id, client_id, service_id, name, status, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:client,:service,'Portal Launch','ONBOARDING',:now,:actor,:now,:actor,1)")
                .param("id", projectId).param("org", organizationA).param("client", clientId).param("service", serviceId).param("now", timestamp(now)).param("actor", actor).update();
        jdbc.sql("INSERT INTO onboarding_templates (id, organization_id, service_id, name, status, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:service,'Portal Flow','ACTIVE',:now,:actor,:now,:actor,0)")
                .param("id", templateId).param("org", organizationA).param("service", serviceId).param("now", timestamp(now)).param("actor", actor).update();
        jdbc.sql("INSERT INTO onboarding_template_versions (id, organization_id, template_id, version_number, status, published_at, published_by, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:template,1,'PUBLISHED',:now,:actor,:now,:actor,:now,:actor,0)")
                .param("id", versionId).param("org", organizationA).param("template", templateId).param("now", timestamp(now)).param("actor", actor).update();
        insertTemplateStep(visibleSource, versionId, actor, now, "WELCOME", "WELCOME", "Welcome to the project", true, 0);
        insertTemplateStep(hiddenSource, versionId, actor, now, "INTERNAL", "MANUAL_TASK", "Internal risk review", false, 1);
        UUID adminSource = UUID.randomUUID();
        insertTemplateStep(adminSource, versionId, actor, now, "ADMIN_GUIDE", "INSTRUCTION", "Admin launch guide", true, 2);
        jdbc.sql("INSERT INTO onboarding_instances (id, organization_id, project_id, source_template_id, source_template_version_id, snapshot_version_number, snapshot_json, status, ready, started_at, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:project,:template,:version,1,'{}','DRAFT',FALSE,:now,:now,:actor,:now,:actor,0)")
                .param("id", onboardingId).param("org", organizationA).param("project", projectId).param("template", templateId).param("version", versionId).param("now", timestamp(now)).param("actor", actor).update();
        insertInstance(UUID.randomUUID(), visibleSource, "WELCOME", "WELCOME", "Welcome to the project", true, 0, actor, now);
        insertInstance(UUID.randomUUID(), hiddenSource, "INTERNAL", "MANUAL_TASK", "Internal risk review", false, 1, actor, now);
        UUID adminStep = UUID.randomUUID();
        insertInstance(adminStep, adminSource, "ADMIN_GUIDE", "INSTRUCTION", "Admin launch guide", true, 2, actor, now);
        jdbc.sql("UPDATE onboarding_step_instances SET assigned_role = 'CLIENT_ADMIN' WHERE id = :id")
                .param("id", adminStep).update();
        jdbc.sql("UPDATE onboarding_step_instances SET status = 'COMPLETED', completed_at = :now WHERE source_step_id = :source")
                .param("now", timestamp(now)).param("source", hiddenSource).update();
    }

    private void insertTemplateStep(UUID id, UUID version, UUID actor, Instant now, String key, String type,
                                    String name, boolean visible, int order) {
        jdbc.sql("INSERT INTO onboarding_template_steps (id, organization_id, template_version_id, step_key, name, step_type, display_order, required, blocking, client_visible, requires_review, dependency_mode, allow_skip, allow_reopen, configuration_json, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:version,:key,:name,:type,:order,TRUE,TRUE,:visible,FALSE,'NONE',FALSE,FALSE,'{}',:now,:actor,:now,:actor,0)")
                .param("id", id).param("org", organizationA).param("version", version).param("key", key)
                .param("name", name).param("type", type).param("order", order).param("visible", visible)
                .param("now", timestamp(now)).param("actor", actor).update();
    }

    private void insertInstance(UUID id, UUID source, String key, String type, String name, boolean visible,
                                int order, UUID actor, Instant now) {
        jdbc.sql("INSERT INTO onboarding_step_instances (id, organization_id, onboarding_id, source_step_id, step_key, name, step_type, display_order, required, blocking, client_visible, requires_review, dependency_mode, allow_skip, allow_reopen, configuration_json, applicable, status, created_at, created_by, updated_at, updated_by, version) VALUES (:id,:org,:onboarding,:source,:key,:name,:type,:order,TRUE,TRUE,:visible,FALSE,'NONE',FALSE,FALSE,'{}',TRUE,'AVAILABLE',:now,:actor,:now,:actor,0)")
                .param("id", id).param("org", organizationA).param("onboarding", onboardingId).param("source", source)
                .param("key", key).param("name", name).param("type", type).param("order", order)
                .param("visible", visible).param("now", timestamp(now)).param("actor", actor).update();
    }

    private UUID directInvitation(String rawToken, Instant expiresAt) {
        UUID id = UUID.randomUUID(); UUID actor = jdbc.sql("SELECT user_id FROM organization_users WHERE organization_id = :org")
                .param("org", organizationA).query(UUID.class).single(); Instant now = Instant.now();
        jdbc.sql("INSERT INTO client_invitations (id, organization_id, client_id, contact_id, project_id, onboarding_id, invited_email, client_role, token_hash, status, delivery_status, expires_at, resend_count, created_at, created_by, updated_at, updated_by, version) SELECT :id,:org,p.client_id,:contact,p.id,:onboarding,'client@portal.test','MEMBER',:hash,'PENDING','SENT',:expires,0,:now,:actor,:now,:actor,0 FROM projects p WHERE p.id = :project")
                .param("id", id).param("org", organizationA).param("contact", contactId).param("onboarding", onboardingId)
                .param("hash", tokens.hash(rawToken)).param("expires", timestamp(expiresAt)).param("now", timestamp(now))
                .param("actor", actor).param("project", projectId).update();
        return id;
    }

    private UUID organization(String slug, String name) {
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        jdbc.sql("INSERT INTO organizations (id, slug, name, status, created_at, updated_at, version) VALUES (:id,:slug,:name,'ACTIVE',:now,:now,0)")
                .param("id", id).param("slug", slug).param("name", name).param("now", timestamp(now)).update();
        return id;
    }

    private Cookie internalSession(UUID organizationId, String email, Set<String> permissions) {
        UUID userId = UUID.randomUUID(), roleId = UUID.randomUUID(), membershipId = UUID.randomUUID(); Instant now = clock.instant();
        jdbc.sql("INSERT INTO users (id,email,display_name,password_hash,principal_type,status,email_verified_at,failed_login_count,credential_version,created_at,updated_at,version) VALUES (:id,:email,'Manager',:password,'INTERNAL','ACTIVE',:now,0,0,:now,:now,0)")
                .param("id", userId).param("email", email).param("password", passwordEncoder.encode("ManagerPortal7Password")).param("now", timestamp(now)).update();
        jdbc.sql("INSERT INTO roles (id,organization_id,name,description,created_at,updated_at,version) VALUES (:id,:org,:name,'',:now,:now,0)")
                .param("id", roleId).param("org", organizationId).param("name", "Portal manager " + email).param("now", timestamp(now)).update();
        for (String permission : permissions) jdbc.sql("INSERT INTO role_permissions (role_id,permission_id,created_at) SELECT :role,id,:now FROM permissions WHERE code=:code")
                .param("role", roleId).param("now", timestamp(now)).param("code", permission).update();
        jdbc.sql("INSERT INTO organization_users (id,organization_id,user_id,role_id,status,invited_at,joined_at,created_at,updated_at,version) VALUES (:id,:org,:user,:role,'ACTIVE',:now,:now,:now,:now,0)")
                .param("id", membershipId).param("org", organizationId).param("user", userId).param("role", roleId).param("now", timestamp(now)).update();
        String raw = tokens.issue(); auth.insertSession(new AuthSession(UUID.randomUUID(), organizationId, userId,
                tokens.hash(raw), 0, now, now, now.plusSeconds(3600), null, now), "ip", "agent");
        return new Cookie("BOS_SESSION", raw);
    }

    private Cookie sessionCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new Cookie("BOS_SESSION", header.substring("BOS_SESSION=".length(), header.indexOf(';')));
    }
    private String tokenBody(String token) throws Exception { return json.writeValueAsString(java.util.Map.of("token", token)); }
    private JsonNode tree(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private void clearData() {
        List.of("client_user_project_access", "client_invitations", "command_idempotency",
                "onboarding_step_instance_dependencies", "onboarding_step_instances", "onboarding_instances",
                "onboarding_step_dependencies", "onboarding_template_steps", "onboarding_template_versions",
                "onboarding_templates", "activity_logs", "project_members", "projects", "client_users",
                "client_primary_contacts", "client_contacts", "clients", "services", "mfa_recovery_codes",
                "mfa_methods", "auth_challenges", "organization_invitation_tokens", "password_reset_tokens",
                "email_verification_tokens", "auth_sessions", "audit_logs", "organization_users",
                "role_permissions", "roles", "users", "organizations")
                .forEach(table -> jdbc.sql("DELETE FROM " + table).update());
    }
}
