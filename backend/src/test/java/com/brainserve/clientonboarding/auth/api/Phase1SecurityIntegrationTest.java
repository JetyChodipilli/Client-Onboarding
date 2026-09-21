package com.brainserve.clientonboarding.auth.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.application.SecurityNotificationPort;
import com.brainserve.clientonboarding.auth.application.TotpService;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityNotificationConfiguration.class)
class Phase1SecurityIntegrationTest {
    private static final String PASSWORD = "CorrectHorse7Battery";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired TotpService totp;
    @Autowired Clock clock;
    @Autowired AuthRepository authRepository;
    @Autowired SecureTokenService tokens;
    @Autowired DataSource dataSource;

    private UUID organizationA;
    private UUID organizationB;
    private UUID writerUser;
    private UUID adminUser;
    private UUID writerRole;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(dataSource).schemas("app").defaultSchema("app")
                .createSchemas(true).locations("classpath:db/migration").load().migrate();
        clearData();
        organizationA = organization("agency-a", "Agency A");
        organizationB = organization("agency-b", "Agency B");
        writerRole = role(organizationA, "Operator", Set.of("ORGANIZATION_READ", "ORGANIZATION_UPDATE"));
        UUID deniedRole = role(organizationA, "Restricted", Set.of());
        UUID adminRole = role(organizationA, "Administrator",
                Set.of("ORGANIZATION_READ", "ORGANIZATION_UPDATE", "USER_MANAGE", "ROLE_MANAGE", "AUDIT_READ"));
        writerUser = user("writer@example.test", "Writer");
        UUID deniedUser = user("denied@example.test", "Denied");
        adminUser = user("admin@example.test", "Admin");
        membership(organizationA, writerUser, writerRole);
        membership(organizationA, deniedUser, deniedRole);
        membership(organizationA, adminUser, adminRole);
        UUID roleB = role(organizationB, "Operator", Set.of("ORGANIZATION_READ", "ORGANIZATION_UPDATE"));
        UUID userB = user("writer-b@example.test", "Writer B");
        membership(organizationB, userB, roleB);
    }

    @Test
    void enforcesAuthenticationPermissionAndTenantIsolationForReadAndUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}", organizationA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        Cookie denied = login("denied@example.test", "agency-a").cookie();
        mockMvc.perform(get("/api/v1/organizations/{id}", organizationA).cookie(denied))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        Cookie writer = login("writer@example.test", "agency-a").cookie();
        mockMvc.perform(get("/api/v1/organizations/{id}", organizationA).cookie(writer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Agency A"));

        mockMvc.perform(get("/api/v1/organizations/{id}", organizationB).cookie(writer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/organizations/{id}", organizationB).cookie(writer).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Escaped\",\"version\":0}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/organizations/{id}", organizationA).cookie(writer).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Agency A Updated\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Agency A Updated"));
    }

    @Test
    void rejectsMissingCsrfAndInvalidCredentialsWithoutLeakingAccountExistence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody("writer@example.test", PASSWORD, "agency-a")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(loginBody("missing@example.test", "WrongPassword7A", "agency-a")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void requiresMfaForPrivilegedUsersAndIssuesRecoveryCodesOnce() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(loginBody("admin@example.test", PASSWORD, "agency-a")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("MFA_ENROLLMENT_REQUIRED"))
                .andReturn();
        JsonNode enrollment = json(first);
        String secret = enrollment.at("/data/enrollmentSecret").asText();
        String challenge = enrollment.at("/data/challengeToken").asText();
        String code = totp.generateCode(secret, clock.instant());

        MvcResult completed = mockMvc.perform(post("/api/v1/auth/mfa/complete").with(csrf())
                        .contentType("application/json")
                        .content(mfaBody(challenge, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.recoveryCodes", hasSize(8)))
                .andReturn();
        Cookie session = sessionCookie(completed);
        String recoveryCode = json(completed).at("/data/recoveryCodes/0").asText();
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk());

        String nextChallenge = json(mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(loginBody("admin@example.test", PASSWORD, "agency-a")))
                .andExpect(status().isAccepted()).andReturn()).at("/data/challengeToken").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/complete").with(csrf())
                        .contentType("application/json").content(mfaBody(nextChallenge, recoveryCode)))
                .andExpect(status().isOk());

        String replayChallenge = json(mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(loginBody("admin@example.test", PASSWORD, "agency-a")))
                .andExpect(status().isAccepted()).andReturn()).at("/data/challengeToken").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/complete").with(csrf())
                        .contentType("application/json").content(mfaBody(replayChallenge, recoveryCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_MFA_CODE"));
    }

    @Test
    void passwordChangeInvalidatesExistingSessions() throws Exception {
        Cookie session = login("writer@example.test", "agency-a").cookie();
        String rawResetToken = tokens.issue();
        Instant now = clock.instant();
        authRepository.insertPasswordResetToken(UUID.randomUUID(), organizationA, writerUser,
                tokens.hash(rawResetToken), now.plusSeconds(600), now);

        mockMvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawResetToken
                                + "\",\"password\":\"ChangedHorse8Battery\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawResetToken
                                + "\",\"password\":\"AnotherHorse9Battery\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SECURITY_TOKEN"));
        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokesPasswordOnlySessionWhenRoleGainsPrivilegedPermission() throws Exception {
        Cookie session = login("writer@example.test", "agency-a").cookie();
        jdbc.sql("""
                INSERT INTO role_permissions (role_id, permission_id, created_at)
                SELECT :roleId, id, CURRENT_TIMESTAMP FROM permissions WHERE code = 'USER_MANAGE'
                """).param("roleId", writerRole).update();

        mockMvc.perform(get("/api/v1/organization-members").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
        Integer revoked = jdbc.sql("SELECT COUNT(*) FROM auth_sessions WHERE user_id = :userId AND revoked_at IS NOT NULL")
                .param("userId", writerUser).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(revoked).isEqualTo(1);
    }

    @Test
    void locksAnAccountAfterRepeatedPasswordFailures() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                            .contentType("application/json")
                            .content(loginBody("writer@example.test", "WrongPassword7A", "agency-a")))
                    .andExpect(status().isUnauthorized());
        }
        Integer failures = jdbc.sql("SELECT failed_login_count FROM users WHERE id = :id")
                .param("id", writerUser).query(Integer.class).single();
        Instant lockedUntil = jdbc.sql("SELECT locked_until FROM users WHERE id = :id")
                .param("id", writerUser).query((rs, rowNum) -> rs.getTimestamp(1).toInstant()).single();
        org.assertj.core.api.Assertions.assertThat(failures).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(lockedUntil).isAfter(clock.instant());

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(loginBody("writer@example.test", PASSWORD, "agency-a")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    private Login login(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json").content(loginBody(email, PASSWORD, slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("AUTHENTICATED"))
                .andReturn();
        return new Login(sessionCookie(result));
    }

    private Cookie sessionCookie(MvcResult result) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("BOS_SESSION=")).findFirst().orElseThrow();
        return new Cookie("BOS_SESSION", header.substring("BOS_SESSION=".length(), header.indexOf(';')));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String loginBody(String email, String password, String slug) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"organizationSlug\":\"" + slug + "\"}";
    }

    private String mfaBody(String challenge, String code) {
        return "{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + code + "\"}";
    }

    private UUID organization(String slug, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO organizations (id, slug, name, status, created_at, updated_at, version)
                VALUES (:id, :slug, :name, 'ACTIVE', :now, :now, 0)
                """).param("id", id).param("slug", slug).param("name", name)
                .param("now", Instant.now()).update();
        return id;
    }

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users (id, email, display_name, password_hash, principal_type, status,
                    email_verified_at, failed_login_count, credential_version, created_at, updated_at, version)
                VALUES (:id, :email, :name, :password, 'INTERNAL', 'ACTIVE', :now, 0, 0, :now, :now, 0)
                """).param("id", id).param("email", email).param("name", name)
                .param("password", passwordEncoder.encode(PASSWORD)).param("now", Instant.now()).update();
        return id;
    }

    private UUID role(UUID organizationId, String name, Set<String> permissions) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO roles (id, organization_id, name, description, created_at, updated_at, version)
                VALUES (:id, :organizationId, :name, '', :now, :now, 0)
                """).param("id", id).param("organizationId", organizationId).param("name", name)
                .param("now", now).update();
        for (String permission : permissions) {
            jdbc.sql("""
                    INSERT INTO role_permissions (role_id, permission_id, created_at)
                    SELECT :roleId, id, :now FROM permissions WHERE code = :code
                    """).param("roleId", id).param("now", now).param("code", permission).update();
        }
        return id;
    }

    private void membership(UUID organizationId, UUID userId, UUID roleId) {
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO organization_users (id, organization_id, user_id, role_id, status,
                    invited_at, joined_at, created_at, updated_at, version)
                VALUES (:id, :organizationId, :userId, :roleId, 'ACTIVE', :now, :now, :now, :now, 0)
                """).param("id", UUID.randomUUID()).param("organizationId", organizationId)
                .param("userId", userId).param("roleId", roleId).param("now", now).update();
    }

    private void clearData() {
        List.of("command_idempotency", "onboarding_step_instance_dependencies", "onboarding_step_instances",
                "onboarding_instances", "onboarding_step_dependencies", "onboarding_template_steps",
                "onboarding_template_versions", "onboarding_templates", "activity_logs", "project_members",
                "projects", "client_users", "client_primary_contacts", "client_contacts", "clients", "services",
                "mfa_recovery_codes", "mfa_methods", "auth_challenges", "organization_invitation_tokens",
                "password_reset_tokens", "email_verification_tokens", "auth_sessions", "audit_logs",
                "organization_users", "role_permissions", "roles", "users", "organizations")
                .forEach(table -> jdbc.sql("DELETE FROM " + table).update());
    }

    private record Login(Cookie cookie) { }
}
