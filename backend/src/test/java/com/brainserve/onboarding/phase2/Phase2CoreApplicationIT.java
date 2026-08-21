package com.brainserve.onboarding.phase2;

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
class Phase2CoreApplicationIT {
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

    private final HttpClient http = HttpClient.newHttpClient();
    private Fixture fixture;

    @BeforeEach
    void seed() {
        fixture = Fixture.create(jdbc, passwordEncoder);
    }

    @Test
    void anonymousAndPermissionlessUsersAreRejected() throws Exception {
        assertThat(get("/api/v1/clients", null).status()).isEqualTo(401);
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.noPermission());
        assertThat(get("/api/v1/clients", token).status()).isEqualTo(403);
        assertThat(get("/api/v1/projects", token).status()).isEqualTo(403);
    }

    @Test
    void clientContactsServicesAndProjectsCompleteThePhase2HappyPath() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());

        JsonNode service = data(post("/api/v1/services", token,
                "{\"code\":\"META_ADS\",\"name\":\"Meta Ads\",\"description\":\"Paid acquisition\"}"));
        JsonNode client = data(post("/api/v1/clients", token, "{\"name\":\"Acme Labs\"}"));
        UUID clientId = UUID.fromString(client.get("id").asText());
        UUID serviceId = UUID.fromString(service.get("id").asText());

        Response contactOne = post("/api/v1/clients/" + clientId + "/contacts", token,
                "{\"displayName\":\"Sara Rao\",\"email\":\"sara@acme.example\",\"jobTitle\":\"Marketing\"}");
        Response contactTwo = post("/api/v1/clients/" + clientId + "/contacts", token,
                "{\"displayName\":\"John Patel\",\"email\":\"john@acme.example\",\"jobTitle\":\"Finance\"}");
        assertThat(contactOne.status()).isEqualTo(200);
        assertThat(contactTwo.status()).isEqualTo(200);
        assertThat(get("/api/v1/clients/" + clientId + "/contacts", token).body()).contains("Sara Rao", "John Patel");
        assertThat(get("/api/v1/clients/" + clientId + "/activity", token).body())
                .contains("CLIENT_CREATED", "CLIENT_CONTACT_CREATED");

        String createProject = "{\"clientId\":\"" + clientId + "\",\"serviceId\":\"" + serviceId
                + "\",\"name\":\"Acme Growth Launch\",\"description\":\"Initial campaign\"}";
        JsonNode project = data(post("/api/v1/projects", token, createProject));
        UUID projectId = UUID.fromString(project.get("id").asText());
        assertThat(project.get("status").asText()).isEqualTo("DRAFT");
        assertThat(project.get("clientName").asText()).isEqualTo("Acme Labs");
        assertThat(project.get("serviceName").asText()).isEqualTo("Meta Ads");

        Response addMember = post("/api/v1/projects/" + projectId + "/members", token,
                "{\"organizationUserId\":\"" + fixture.managerA().memberId() + "\",\"responsibility\":\"Account owner\"}");
        assertThat(addMember.status()).isEqualTo(200);
        assertThat(get("/api/v1/projects/" + projectId + "/members", token).body()).contains("Account owner");
        assertThat(get("/api/v1/projects/" + projectId + "/activity", token).body()).contains("PROJECT_CREATED", "PROJECT_MEMBER_ADDED");
    }

    @Test
    void serverSideSearchIsPaginatedAndTenantScoped() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        UUID service = fixture.insertService(jdbc, fixture.orgA(), fixture.managerA().userId(), "ANALYTICS_CORE", "Analytics Core");
        UUID matchingClient = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Needle Company");
        fixture.insertProject(jdbc, fixture.orgA(), matchingClient, service, fixture.managerA().userId());

        UUID foreignService = fixture.insertService(jdbc, fixture.orgB(), fixture.managerB().userId(), "ANALYTICS_FOREIGN", "Analytics Foreign");
        UUID foreignClient = fixture.insertClient(jdbc, fixture.orgB(), fixture.managerB().userId(), "Needle Foreign");
        fixture.insertProject(jdbc, fixture.orgB(), foreignClient, foreignService, fixture.managerB().userId());

        for (int i = 0; i < 27; i++) {
            fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Searchable " + String.format("%02d", i));
        }

        JsonNode clientPage = objectMapper.readTree(get("/api/v1/clients?query=Searchable&size=10&page=1", token).body());
        assertThat(clientPage.get("data").size()).isEqualTo(10);
        assertThat(clientPage.get("meta").get("totalElements").asLong()).isEqualTo(27);
        assertThat(clientPage.get("meta").get("totalPages").asInt()).isEqualTo(3);

        Response projectSearch = get("/api/v1/projects?query=Needle&size=10", token);
        assertThat(projectSearch.status()).isEqualTo(200);
        assertThat(projectSearch.body()).contains("Needle Company");
        assertThat(projectSearch.body()).doesNotContain("Needle Foreign");

        Response serviceSearch = get("/api/v1/services?query=ANALYTICS_CORE&size=10", token);
        assertThat(serviceSearch.status()).isEqualTo(200);
        assertThat(serviceSearch.body()).contains("Analytics Core");
        assertThat(serviceSearch.body()).doesNotContain("Analytics Foreign");
    }

    @Test
    void tenantACannotReadOrUpdateTenantBClientAndCannotUseForeignRelationships() throws Exception {
        UUID foreignClient = fixture.insertClient(jdbc, fixture.orgB(), fixture.managerB().userId(), "Foreign Client");
        UUID foreignService = fixture.insertService(jdbc, fixture.orgB(), fixture.managerB().userId(), "FOREIGN", "Foreign Service");
        UUID foreignProject = fixture.insertProject(jdbc, fixture.orgB(), foreignClient, foreignService, fixture.managerB().userId());
        UUID localClient = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Local Client");
        UUID localService = fixture.insertService(jdbc, fixture.orgA(), fixture.managerA().userId(), "LOCAL", "Local Service");
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());

        assertThat(get("/api/v1/clients/" + foreignClient, token).status()).isEqualTo(404);
        assertThat(patch("/api/v1/clients/" + foreignClient, token, "{\"name\":\"Attack\",\"status\":\"ACTIVE\",\"version\":0}").status()).isEqualTo(404);
        assertThat(get("/api/v1/projects/" + foreignProject, token).status()).isEqualTo(404);
        assertThat(patch("/api/v1/projects/" + foreignProject, token,
                "{\"clientId\":\"" + foreignClient + "\",\"serviceId\":\"" + foreignService
                        + "\",\"name\":\"Attack\",\"version\":0}").status()).isEqualTo(404);

        Response foreignClientProject = post("/api/v1/projects", token,
                "{\"clientId\":\"" + foreignClient + "\",\"serviceId\":\"" + localService + "\",\"name\":\"Leak\"}");
        assertThat(foreignClientProject.status()).isEqualTo(404);
        Response foreignServiceProject = post("/api/v1/projects", token,
                "{\"clientId\":\"" + localClient + "\",\"serviceId\":\"" + foreignService + "\",\"name\":\"Leak\"}");
        assertThat(foreignServiceProject.status()).isEqualTo(404);
    }

    @Test
    void databaseRejectsCrossTenantProjectAndMemberRelationships() {
        UUID clientB = fixture.insertClient(jdbc, fixture.orgB(), fixture.managerB().userId(), "B Client");
        UUID serviceA = fixture.insertService(jdbc, fixture.orgA(), fixture.managerA().userId(), "A_SERVICE", "A Service");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO client_onboarding.projects
                    (id, organization_id, client_id, service_id, name, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'Cross Tenant', ?, ?)
                """, UUID.randomUUID(), fixture.orgA(), clientB, serviceA, fixture.managerA().userId(), fixture.managerA().userId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID clientA = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "A Client");
        UUID projectA = fixture.insertProject(jdbc, fixture.orgA(), clientA, serviceA, fixture.managerA().userId());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO client_onboarding.project_members
                    (id, organization_id, project_id, organization_user_id, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), fixture.orgA(), projectA, fixture.managerB().memberId(), fixture.managerA().userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clientArchiveIsSoftAndProjectArchiveRequiresLifecycleOrder() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        UUID client = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Archive Client");
        Response archived = post("/api/v1/clients/" + client + "/archive", token, "{\"version\":0}");
        assertThat(archived.status()).isEqualTo(200);
        assertThat(archived.body()).contains("ARCHIVED");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM client_onboarding.clients WHERE organization_id=? AND id=?", Integer.class, fixture.orgA(), client);
        assertThat(count).isEqualTo(1);

        UUID usableClient = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Project Client");
        UUID service = fixture.insertService(jdbc, fixture.orgA(), fixture.managerA().userId(), "ARCHIVE_SVC", "Archive Service");
        UUID project = fixture.insertProject(jdbc, fixture.orgA(), usableClient, service, fixture.managerA().userId());
        Response directArchive = post("/api/v1/projects/" + project + "/archive", token, "{\"version\":0}");
        assertThat(directArchive.status()).isEqualTo(409);
        Response cancelled = post("/api/v1/projects/" + project + "/cancel", token, "{\"version\":0}");
        assertThat(cancelled.status()).isEqualTo(200);
        long version = data(cancelled).get("version").asLong();
        Response projectArchived = post("/api/v1/projects/" + project + "/archive", token, "{\"version\":" + version + "}");
        assertThat(projectArchived.status()).isEqualTo(200);
        assertThat(projectArchived.body()).contains("ARCHIVED");
    }

    @Test
    void archivedServiceCannotBeAssignedToNewProject() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        UUID client = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Active Client");
        UUID service = fixture.insertService(jdbc, fixture.orgA(), fixture.managerA().userId(), "OLD_SVC", "Old Service");
        Response archived = post("/api/v1/services/" + service + "/archive", token, "{\"version\":0}");
        assertThat(archived.status()).isEqualTo(200);
        Response project = post("/api/v1/projects", token,
                "{\"clientId\":\"" + client + "\",\"serviceId\":\"" + service + "\",\"name\":\"Must fail\"}");
        assertThat(project.status()).isEqualTo(409);
        assertThat(project.body()).contains("SERVICE_ARCHIVED");
    }

    @Test
    void existingProjectRemainsEditableWhenReferencedClientOrServiceIsLaterArchived() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        UUID client = fixture.insertClient(jdbc, fixture.orgA(), fixture.managerA().userId(), "Legacy Client");
        UUID service = fixture.insertService(jdbc, fixture.orgA(), fixture.managerA().userId(), "LEGACY_SVC", "Legacy Service");
        UUID project = fixture.insertProject(jdbc, fixture.orgA(), client, service, fixture.managerA().userId());
        jdbc.update("UPDATE client_onboarding.projects SET status='ONBOARDING' WHERE organization_id=? AND id=?", fixture.orgA(), project);

        assertThat(post("/api/v1/clients/" + client + "/archive", token, "{\"version\":0}").status()).isEqualTo(200);
        assertThat(post("/api/v1/services/" + service + "/archive", token, "{\"version\":0}").status()).isEqualTo(200);

        Response update = patch("/api/v1/projects/" + project, token,
                "{\"clientId\":\"" + client + "\",\"serviceId\":\"" + service
                        + "\",\"name\":\"Legacy Project Renamed\",\"description\":\"Still valid\",\"version\":0}");
        assertThat(update.status()).isEqualTo(200);
        assertThat(update.body()).contains("Legacy Project Renamed", "ONBOARDING");
    }

    private JsonNode data(Response response) throws Exception {
        assertThat(response.status()).isEqualTo(200);
        return objectMapper.readTree(response.body()).get("data");
    }

    private Response get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        auth(builder, token);
        return send(builder.build());
    }

    private Response post(String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        auth(builder, token);
        return send(builder.build());
    }

    private Response patch(String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        auth(builder, token);
        return send(builder.build());
    }

    private void auth(HttpRequest.Builder builder, String token) {
        if (token != null) builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private record Response(int status, String body) {}

    private record Fixture(UUID orgA, UUID orgB, Identity managerA, Identity managerB, Identity noPermission) {
        static Fixture create(JdbcTemplate jdbc, PasswordEncoder encoder) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UUID orgA = UUID.randomUUID();
            UUID orgB = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)", orgA, "Phase2 A", "phase2-a-" + suffix);
            jdbc.update("INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)", orgB, "Phase2 B", "phase2-b-" + suffix);
            Identity managerA = identity(jdbc, encoder, orgA, "manager-a-" + suffix + "@example.com");
            Identity managerB = identity(jdbc, encoder, orgB, "manager-b-" + suffix + "@example.com");
            Identity none = identity(jdbc, encoder, orgA, "none-" + suffix + "@example.com");
            UUID roleA = role(jdbc, orgA, managerA.userId(), "PHASE2_MANAGER_A_" + suffix,
                    List.of("CLIENT_CREATE", "CLIENT_READ", "CLIENT_UPDATE", "SERVICE_READ", "SERVICE_MANAGE", "PROJECT_CREATE", "PROJECT_READ", "PROJECT_UPDATE"));
            UUID roleB = role(jdbc, orgB, managerB.userId(), "PHASE2_MANAGER_B_" + suffix,
                    List.of("CLIENT_CREATE", "CLIENT_READ", "CLIENT_UPDATE", "SERVICE_READ", "SERVICE_MANAGE", "PROJECT_CREATE", "PROJECT_READ", "PROJECT_UPDATE"));
            assign(jdbc, orgA, managerA.memberId(), roleA, managerA.userId());
            assign(jdbc, orgB, managerB.memberId(), roleB, managerB.userId());
            return new Fixture(orgA, orgB, managerA, managerB, none);
        }

        String token(JdbcTemplate jdbc, JwtEncoder encoder, SecurityProperties props, Identity identity) {
            UUID org = jdbc.queryForObject("SELECT organization_id FROM client_onboarding.organization_users WHERE id=?", UUID.class, identity.memberId());
            UUID sessionId = UUID.randomUUID();
            String refreshHash = CryptoSupport.sha256Hex(UUID.randomUUID().toString() + UUID.randomUUID());
            jdbc.update("""
                    INSERT INTO client_onboarding.auth_sessions
                      (id, organization_id, user_id, organization_user_id, refresh_token_hash, credentials_version, expires_at)
                    VALUES (?, ?, ?, ?, ?, 0, ?)
                    """, sessionId, org, identity.userId(), identity.memberId(), refreshHash, Instant.now().plusSeconds(3600));
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer(props.tokenIssuer()).issuedAt(now).expiresAt(now.plusSeconds(900))
                    .subject(identity.userId().toString()).audience(List.of(props.tokenAudience()))
                    .claim("org", org.toString()).claim("mid", identity.memberId().toString())
                    .claim("sid", sessionId.toString()).claim("cv", 0L).build();
            return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        }

        UUID insertClient(JdbcTemplate jdbc, UUID org, UUID actor, String name) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.clients(id,organization_id,name,created_by,updated_by) VALUES (?,?,?,?,?)", id, org, name, actor, actor);
            return id;
        }

        UUID insertService(JdbcTemplate jdbc, UUID org, UUID actor, String code, String name) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.services(id,organization_id,code,name,created_by,updated_by) VALUES (?,?,?,?,?,?)", id, org, code, name, actor, actor);
            return id;
        }

        UUID insertProject(JdbcTemplate jdbc, UUID org, UUID client, UUID service, UUID actor) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.projects(id,organization_id,client_id,service_id,name,created_by,updated_by) VALUES (?,?,?,?,?,?,?)",
                    id, org, client, service, "Phase 2 Project", actor, actor);
            return id;
        }

        private static Identity identity(JdbcTemplate jdbc, PasswordEncoder encoder, UUID org, String email) {
            UUID user = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            Instant now = Instant.now();
            jdbc.update("""
                    INSERT INTO client_onboarding.users
                      (id,email,normalized_email,display_name,password_hash,email_verified_at)
                    VALUES (?,?,?,?,?,?)
                    """, user, email, email, "Phase2 User", encoder.encode("Velvet-River-84-Comet"), now);
            jdbc.update("INSERT INTO client_onboarding.organization_users(id,organization_id,user_id,status,joined_at) VALUES (?,?,?,'ACTIVE',?)",
                    member, org, user, now);
            return new Identity(user, member);
        }

        private static UUID role(JdbcTemplate jdbc, UUID org, UUID actor, String code, List<String> permissions) {
            UUID role = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.roles(id,organization_id,code,name,system_role,created_by,updated_by) VALUES (?,?,?,?,false,?,?)",
                    role, org, code, code, actor, actor);
            for (String permission : permissions) {
                UUID permissionId = jdbc.queryForObject("SELECT id FROM client_onboarding.permissions WHERE code=?", UUID.class, permission);
                jdbc.update("INSERT INTO client_onboarding.role_permissions(organization_id,role_id,permission_id,created_by) VALUES (?,?,?,?)",
                        org, role, permissionId, actor);
            }
            return role;
        }

        private static void assign(JdbcTemplate jdbc, UUID org, UUID member, UUID role, UUID actor) {
            jdbc.update("INSERT INTO client_onboarding.organization_user_roles(organization_id,organization_user_id,role_id,created_by) VALUES (?,?,?,?)",
                    org, member, role, actor);
        }
    }

    private record Identity(UUID userId, UUID memberId) {}
}
