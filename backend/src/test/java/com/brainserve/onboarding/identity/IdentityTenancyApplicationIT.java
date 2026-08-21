package com.brainserve.onboarding.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.common.security.SecurityProperties;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityTenancyApplicationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("client_onboarding").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.require-explicit-secrets", () -> "false");
        registry.add("app.security.secure-cookies", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SecurityProperties securityProperties;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private Fixture fixture;

    @BeforeEach
    void seed() {
        fixture = Fixture.create(jdbc, passwordEncoder);
    }

    @Test
    void unauthorizedUserIsRejected() throws Exception {
        Response response = get("/api/v1/organizations/" + fixture.orgA(), null);
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHORIZED");
    }

    @Test
    void userWithoutPermissionIsRejectedAndValidPermissionIsAccepted() throws Exception {
        String noPermissions = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userNoPermission(), fixture.orgA(), fixture.memberNoPermission());
        assertThat(get("/api/v1/organizations/" + fixture.orgA(), noPermissions).status()).isEqualTo(403);

        String reader = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userReader(), fixture.orgA(), fixture.memberReader());
        Response accepted = get("/api/v1/organizations/" + fixture.orgA(), reader);
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.body()).contains(fixture.slugA());
    }

    @Test
    void organizationACannotReadOrUpdateOrganizationB() throws Exception {
        String reader = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userReader(), fixture.orgA(), fixture.memberReader());
        assertThat(get("/api/v1/organizations/" + fixture.orgB(), reader).status()).isEqualTo(404);

        String manager = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userManager(), fixture.orgA(), fixture.memberManager());
        Response update = patch("/api/v1/organizations/" + fixture.orgB(), manager, "{\"name\":\"Intrusion\",\"version\":0}");
        assertThat(update.status()).isEqualTo(404);
    }

    @Test
    void permissionRevocationAndMembershipSuspensionTakeEffectWithoutWaitingForJwtExpiry() throws Exception {
        String reader = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userReader(), fixture.orgA(), fixture.memberReader());
        assertThat(get("/api/v1/organizations/" + fixture.orgA(), reader).status()).isEqualTo(200);

        jdbc.update("DELETE FROM client_onboarding.role_permissions WHERE organization_id=? AND role_id=? AND permission_id=?",
                fixture.orgA(), fixture.roleReader(), Fixture.permissionId("ORG_READ"));
        assertThat(get("/api/v1/organizations/" + fixture.orgA(), reader).status()).isEqualTo(403);

        String noPermissions = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userNoPermission(), fixture.orgA(), fixture.memberNoPermission());
        jdbc.update("UPDATE client_onboarding.organization_users SET status='SUSPENDED' WHERE organization_id=? AND id=?",
                fixture.orgA(), fixture.memberNoPermission());
        assertThat(get("/api/v1/auth/me", noPermissions).status()).isEqualTo(401);
    }

    @Test
    void roleManagerCannotDelegatePermissionTheyDoNotHold() throws Exception {
        String roleManager = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userRoleManager(), fixture.orgA(), fixture.memberRoleManager());
        String payload = "{\"code\":\"ESCALATED\",\"name\":\"Escalated\",\"description\":\"must fail\",\"permissionIds\":[\"" + Fixture.permissionId("USER_MANAGE") + "\"]}";
        Response response = post("/api/v1/roles", roleManager, payload, null);
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("PERMISSION_DELEGATION_FORBIDDEN");
    }

    @Test
    void databaseRejectsCrossTenantSessionRelationship() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO client_onboarding.auth_sessions
                  (id, organization_id, user_id, organization_user_id, refresh_token_hash, credentials_version, expires_at)
                VALUES (?, ?, ?, ?, ?, 0, ?)
                """, UUID.randomUUID(), fixture.orgA(), fixture.userReader(), fixture.memberB(), "f".repeat(64), Instant.now().plusSeconds(3600)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditRowsAreAppendOnlyAtDatabaseBoundary() {
        UUID auditId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO client_onboarding.audit_logs
                  (id, organization_id, actor_user_id, action, entity_type, request_id, correlation_id, source)
                VALUES (?, ?, ?, 'TEST', 'USER', 'r', 'c', 'TEST')
                """, auditId, fixture.orgA(), fixture.userReader());
        assertThatThrownBy(() -> jdbc.update("UPDATE client_onboarding.audit_logs SET action='TAMPERED' WHERE id=?", auditId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void loginRefreshRotationAndReuseDetectionWork() throws Exception {
        String payload = "{\"email\":\"" + fixture.readerEmail() + "\",\"organizationSlug\":\"" + fixture.slugA() + "\",\"password\":\"Velvet-River-84-Comet\"}";
        Response login = post("/api/v1/auth/login", null, payload, null);
        assertThat(login.status()).isEqualTo(200);
        JsonNode loginJson = objectMapper.readTree(login.body());
        assertThat(loginJson.at("/data/accessToken").asText()).isNotBlank();
        String firstCookie = cookieValue(login.setCookie());

        Response refresh = post("/api/v1/auth/refresh", null, null, firstCookie);
        assertThat(refresh.status()).isEqualTo(200);
        String secondCookie = cookieValue(refresh.setCookie());
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        Response reused = post("/api/v1/auth/refresh", null, null, firstCookie);
        assertThat(reused.status()).isEqualTo(401);
        assertThat(reused.body()).contains("SESSION_REUSE_DETECTED");

        Response revokedReplacement = post("/api/v1/auth/refresh", null, null, secondCookie);
        assertThat(revokedReplacement.status()).isEqualTo(401);
    }

    @Test
    void wrongPasswordReturnsGenericAuthenticationError() throws Exception {
        String payload = "{\"email\":\"" + fixture.readerEmail() + "\",\"organizationSlug\":\"" + fixture.slugA() + "\",\"password\":\"Definitely-Wrong-Password\"}";
        Response response = post("/api/v1/auth/login", null, payload, null);
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).contains("INVALID_CREDENTIALS");
        assertThat(response.body()).doesNotContain(fixture.readerEmail());
        Integer failed = jdbc.queryForObject("SELECT failed_login_count FROM client_onboarding.users WHERE id=?", Integer.class, fixture.userReader());
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void consumedPasswordResetTokenCannotBeReplayed() throws Exception {
        String rawToken = "reset-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO client_onboarding.security_tokens
                  (id, organization_id, user_id, token_type, token_hash, expires_at)
                VALUES (?, ?, ?, 'PASSWORD_RESET', ?, ?)
                """, UUID.randomUUID(), fixture.orgA(), fixture.userReader(), CryptoSupport.sha256Hex(rawToken), Instant.now().plusSeconds(600));
        String payload = "{\"token\":\"" + rawToken + "\",\"newPassword\":\"New-Velvet-River-85-Comet\"}";
        assertThat(post("/api/v1/auth/reset-password", null, payload, null).status()).isEqualTo(200);
        Response replay = post("/api/v1/auth/reset-password", null, payload, null);
        assertThat(replay.status()).isEqualTo(400);
        assertThat(replay.body()).contains("SECURITY_TOKEN_INVALID");
    }

    @Test
    void organizationCannotAssignRolesToForeignTenantMembership() throws Exception {
        String manager = fixture.sessionToken(jdbc, jwtEncoder, securityProperties, fixture.userManager(), fixture.orgA(), fixture.memberManager());
        String payload = "{\"roleIds\":[\"" + fixture.roleReader() + "\"],\"version\":0}";
        Response response = put("/api/v1/organization-users/" + fixture.memberB() + "/roles", manager, payload);
        assertThat(response.status()).isEqualTo(404);
    }

    private Response get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return send(request.build());
    }

    private Response patch(String path, String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return send(request.build());
    }

    private Response put(String path, String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).PUT(HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return send(request.build());
    }

    private Response post(String path, String token, String json, String refreshCookie) throws Exception {
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).POST(body);
        if (json != null) request.header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (refreshCookie != null) request.header(HttpHeaders.COOKIE, "co_refresh=" + refreshCookie);
        return send(request.build());
    }

    private Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers().firstValue("set-cookie").orElse(null));
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private static String cookieValue(String setCookie) {
        assertThat(setCookie).isNotBlank();
        return setCookie.substring("co_refresh=".length(), setCookie.indexOf(';'));
    }

    private record Response(int status, String body, String setCookie) {}

    private record Fixture(
            UUID orgA, UUID orgB, String slugA,
            UUID userReader, UUID memberReader, UUID roleReader,
            UUID userNoPermission, UUID memberNoPermission,
            UUID userManager, UUID memberManager,
            UUID userRoleManager, UUID memberRoleManager,
            UUID memberB, String readerEmail) {

        static Fixture create(JdbcTemplate jdbc, PasswordEncoder encoder) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UUID orgA = UUID.randomUUID(), orgB = UUID.randomUUID();
            String slugA = "org-a-" + suffix;
            jdbc.update("INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)", orgA, "Org A " + suffix, slugA);
            jdbc.update("INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)", orgB, "Org B " + suffix, "org-b-" + suffix);

            Identity reader = identity(jdbc, encoder, orgA, "reader-" + suffix + "@example.com", false);
            Identity none = identity(jdbc, encoder, orgA, "none-" + suffix + "@example.com", false);
            Identity manager = identity(jdbc, encoder, orgA, "manager-" + suffix + "@example.com", true);
            Identity roleManager = identity(jdbc, encoder, orgA, "role-manager-" + suffix + "@example.com", true);
            Identity b = identity(jdbc, encoder, orgB, "b-" + suffix + "@example.com", true);

            UUID roleReader = role(jdbc, orgA, "READER_" + suffix, reader.userId(), List.of("ORG_READ"));
            role(jdbc, orgA, "NONE_" + suffix, none.userId(), List.of());
            UUID managerRole = role(jdbc, orgA, "MANAGER_" + suffix, manager.userId(), List.of("ORG_READ", "ORG_UPDATE", "USER_MANAGE"));
            UUID roleManagerRole = role(jdbc, orgA, "ROLE_MANAGER_" + suffix, roleManager.userId(), List.of("ROLE_MANAGE"));
            assign(jdbc, orgA, reader.memberId(), roleReader, reader.userId());
            assign(jdbc, orgA, manager.memberId(), managerRole, manager.userId());
            assign(jdbc, orgA, roleManager.memberId(), roleManagerRole, roleManager.userId());
            return new Fixture(orgA, orgB, slugA, reader.userId(), reader.memberId(), roleReader,
                    none.userId(), none.memberId(), manager.userId(), manager.memberId(),
                    roleManager.userId(), roleManager.memberId(), b.memberId(), reader.email());
        }

        String sessionToken(JdbcTemplate jdbc, JwtEncoder encoder, SecurityProperties props, UUID userId, UUID orgId, UUID memberId) {
            UUID sessionId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO client_onboarding.auth_sessions
                      (id, organization_id, user_id, organization_user_id, refresh_token_hash, credentials_version, expires_at)
                    VALUES (?, ?, ?, ?, ?, 0, ?)
                    """, sessionId, orgId, userId, memberId, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""), Instant.now().plusSeconds(3600));
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer(props.tokenIssuer()).issuedAt(now).expiresAt(now.plusSeconds(900))
                    .subject(userId.toString()).audience(List.of(props.tokenAudience()))
                    .claim("org", orgId.toString()).claim("mid", memberId.toString()).claim("sid", sessionId.toString()).claim("cv", 0L).build();
            return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        }

        static UUID permissionId(String code) {
            return switch (code) {
                case "ORG_READ" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                case "ORG_UPDATE" -> UUID.fromString("00000000-0000-0000-0000-000000000002");
                case "USER_MANAGE" -> UUID.fromString("00000000-0000-0000-0000-000000000004");
                case "ROLE_MANAGE" -> UUID.fromString("00000000-0000-0000-0000-000000000006");
                default -> throw new IllegalArgumentException(code);
            };
        }

        private static Identity identity(JdbcTemplate jdbc, PasswordEncoder encoder, UUID org, String email, boolean mfa) {
            UUID user = UUID.randomUUID(), member = UUID.randomUUID();
            Instant now = Instant.now();
            jdbc.update("""
                    INSERT INTO client_onboarding.users
                      (id,email,normalized_email,display_name,password_hash,email_verified_at,mfa_enabled,mfa_secret_encrypted)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, user, email, email, "Test User", encoder.encode("Velvet-River-84-Comet"), now, mfa, mfa ? "test-secret-not-used" : null);
            jdbc.update("INSERT INTO client_onboarding.organization_users(id,organization_id,user_id,status,joined_at) VALUES (?,?,?,'ACTIVE',?)", member, org, user, now);
            return new Identity(user, member, email);
        }

        private static UUID role(JdbcTemplate jdbc, UUID org, String code, UUID actor, List<String> permissionCodes) {
            UUID role = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.roles(id,organization_id,code,name,system_role,created_by,updated_by) VALUES (?,?,?,?,false,?,?)",
                    role, org, code, code, actor, actor);
            for (String permission : permissionCodes) {
                jdbc.update("INSERT INTO client_onboarding.role_permissions(organization_id,role_id,permission_id,created_by) VALUES (?,?,?,?)",
                        org, role, permissionId(permission), actor);
            }
            return role;
        }

        private static void assign(JdbcTemplate jdbc, UUID org, UUID member, UUID role, UUID actor) {
            jdbc.update("INSERT INTO client_onboarding.organization_user_roles(organization_id,organization_user_id,role_id,created_by) VALUES (?,?,?,?)",
                    org, member, role, actor);
        }

        private record Identity(UUID userId, UUID memberId, String email) {}
    }
}
