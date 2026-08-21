package com.brainserve.onboarding.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.common.security.SecurityProperties;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.onboarding.application.service.OnboardingLifecycleService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
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
class Phase3WorkflowApplicationIT {
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
    @Autowired OnboardingLifecycleService lifecycle;
    @Autowired OnboardingStepCommandService stepCommands;
    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private Fixture fixture;

    @BeforeEach
    void seed() { fixture = Fixture.create(jdbc, passwordEncoder); }

    @Test
    void anonymousPermissionlessAndCrossTenantRequestsAreRejected() throws Exception {
        assertThat(get("/api/v1/workflow-templates", null).status()).isEqualTo(401);
        String none = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.noPermission());
        assertThat(get("/api/v1/workflow-templates", none).status()).isEqualTo(403);

        String tokenA = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        String tokenB = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerB());
        Workflow workflowB = createPublishedWorkflow(tokenB, "Tenant B Workflow", simpleSteps("B Base"));
        assertThat(get("/api/v1/workflow-templates/" + workflowB.templateId(), tokenA).status()).isEqualTo(404);
        Response foreignStart = post("/api/v1/projects/" + fixture.projectA() + "/onboarding", tokenA,
                "{\"templateVersionId\":\"" + workflowB.versionId() + "\",\"projectVersion\":0}", "phase3-cross-tenant-0001");
        assertThat(foreignStart.status()).isEqualTo(404);
    }

    @Test
    void sequentialParallelAnyConditionalAndReadinessRulesWorkTogether() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        String steps = """
                [
                  {"stepKey":"A","name":"A","stepType":"MANUAL_TASK","displayOrder":0,"required":true,"blocking":true,"clientVisible":true,"requiresReview":false,"dependencyMode":"NONE","allowSkip":false,"allowReopen":false,"dependencyKeys":[],"configuration":{}},
                  {"stepKey":"B","name":"B","stepType":"MANUAL_TASK","displayOrder":1,"required":true,"blocking":true,"clientVisible":true,"requiresReview":false,"dependencyMode":"NONE","allowSkip":false,"allowReopen":false,"dependencyKeys":[],"configuration":{}},
                  {"stepKey":"C","name":"C all","stepType":"APPROVAL","displayOrder":2,"required":true,"blocking":true,"clientVisible":false,"requiresReview":false,"dependencyMode":"ALL","allowSkip":false,"allowReopen":false,"dependencyKeys":["A","B"],"configuration":{}},
                  {"stepKey":"D","name":"D any","stepType":"INSTRUCTION","displayOrder":3,"required":false,"blocking":false,"clientVisible":true,"requiresReview":false,"dependencyMode":"ANY","allowSkip":true,"allowReopen":false,"dependencyKeys":["A","B"],"configuration":{}},
                  {"stepKey":"E","name":"Disabled by condition","stepType":"MANUAL_TASK","displayOrder":4,"required":true,"blocking":true,"clientVisible":true,"requiresReview":false,"dependencyMode":"NONE","conditionExpression":{"op":"EQ","field":"service.code","value":"OTHER"},"allowSkip":false,"allowReopen":false,"dependencyKeys":[],"configuration":{}},
                  {"stepKey":"F","name":"Optional info","stepType":"INSTRUCTION","displayOrder":5,"required":false,"blocking":false,"clientVisible":true,"requiresReview":false,"dependencyMode":"NONE","allowSkip":true,"allowReopen":false,"dependencyKeys":[],"configuration":{}}
                ]
                """;
        Workflow workflow = createPublishedWorkflow(token, "Complex Workflow", steps);
        JsonNode onboarding = data(post("/api/v1/projects/" + fixture.projectA() + "/onboarding", token,
                "{\"templateVersionId\":\"" + workflow.versionId() + "\",\"projectVersion\":0}", "phase3-complex-0001"));
        UUID onboardingId = UUID.fromString(onboarding.get("id").asText());
        assertThat(onboarding.get("steps").size()).isEqualTo(5);
        assertThat(onboarding.toString()).doesNotContain("Disabled by condition");
        assertThat(status(onboarding, "A")).isEqualTo("AVAILABLE");
        assertThat(status(onboarding, "B")).isEqualTo("AVAILABLE");
        assertThat(status(onboarding, "C")).isEqualTo("LOCKED");
        assertThat(status(onboarding, "D")).isEqualTo("LOCKED");

        lifecycle.transition(fixture.orgA(), onboardingId, OnboardingStatus.INVITED, fixture.managerA().userId());
        lifecycle.transition(fixture.orgA(), onboardingId, OnboardingStatus.IN_PROGRESS, fixture.managerA().userId());

        UUID a = stepId(onboarding, "A");
        UUID b = stepId(onboarding, "B");
        UUID c = stepId(onboarding, "C");
        stepCommands.transition(fixture.orgA(), onboardingId, a, OnboardingStepStatus.COMPLETED, fixture.managerA().userId());
        JsonNode afterA = data(get("/api/v1/onboardings/" + onboardingId, token));
        assertThat(status(afterA, "D")).isEqualTo("AVAILABLE");
        assertThat(status(afterA, "C")).isEqualTo("LOCKED");
        assertThat(afterA.get("ready").asBoolean()).isFalse();

        stepCommands.transition(fixture.orgA(), onboardingId, b, OnboardingStepStatus.COMPLETED, fixture.managerA().userId());
        JsonNode afterB = data(get("/api/v1/onboardings/" + onboardingId, token));
        assertThat(status(afterB, "C")).isEqualTo("AVAILABLE");
        stepCommands.transition(fixture.orgA(), onboardingId, c, OnboardingStepStatus.COMPLETED, fixture.managerA().userId());
        JsonNode ready = data(get("/api/v1/onboardings/" + onboardingId, token));
        assertThat(ready.get("ready").asBoolean()).isTrue();
        assertThat(ready.get("status").asText()).isEqualTo("AWAITING_INTERNAL_REVIEW");
        assertThat(status(ready, "F")).isEqualTo("AVAILABLE");
    }

    @Test
    void phase11FinalReviewCompletesOnboardingThenRequiresSeparatePrivilegedActivation() throws Exception {
        String tokenA = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        String tokenB = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerB());
        String reviewStep = """
                [
                  {"stepKey":"APPROVAL","name":"Blocking internal approval","stepType":"APPROVAL","displayOrder":0,
                   "required":true,"blocking":true,"clientVisible":false,"requiresReview":false,"dependencyMode":"NONE",
                   "allowSkip":false,"allowReopen":false,"dependencyKeys":[],"configuration":{}},
                  {"stepKey":"REQUIRED_NON_BLOCKING","name":"Required non-blocking handoff","stepType":"APPROVAL","displayOrder":1,
                   "required":true,"blocking":false,"clientVisible":false,"requiresReview":false,"dependencyMode":"NONE",
                   "allowSkip":false,"allowReopen":false,"dependencyKeys":[],"configuration":{}}
                ]
                """;
        Workflow workflow = createPublishedWorkflow(tokenA, "Phase 11 Review Workflow", reviewStep);
        JsonNode onboarding = data(post("/api/v1/projects/" + fixture.projectA() + "/onboarding", tokenA,
                "{\"templateVersionId\":\"" + workflow.versionId() + "\",\"projectVersion\":0}",
                "phase11-review-0001"));
        UUID onboardingId = UUID.fromString(onboarding.get("id").asText());
        lifecycle.transition(fixture.orgA(), onboardingId, OnboardingStatus.INVITED, fixture.managerA().userId());
        lifecycle.transition(fixture.orgA(), onboardingId, OnboardingStatus.IN_PROGRESS, fixture.managerA().userId());
        stepCommands.transition(fixture.orgA(), onboardingId, stepId(onboarding, "APPROVAL"),
                OnboardingStepStatus.COMPLETED, fixture.managerA().userId());

        JsonNode checklist = data(get("/api/v1/projects/" + fixture.projectA() + "/review", tokenA));
        assertThat(checklist.get("onboardingStatus").asText()).isEqualTo("AWAITING_INTERNAL_REVIEW");
        assertThat(checklist.get("ready").asBoolean()).isTrue();
        assertThat(checklist.get("canApprove").asBoolean()).isFalse();
        assertThat(post("/api/v1/onboardings/" + onboardingId + "/approve", tokenA,
                "{\"version\":" + checklist.get("onboardingVersion").asLong() + "}", null).status()).isEqualTo(409);
        assertThat(get("/api/v1/projects/" + fixture.projectA() + "/review", tokenB).status()).isEqualTo(404);

        stepCommands.transition(fixture.orgA(), onboardingId, stepId(onboarding, "REQUIRED_NON_BLOCKING"),
                OnboardingStepStatus.COMPLETED, fixture.managerA().userId());
        checklist = data(get("/api/v1/projects/" + fixture.projectA() + "/review", tokenA));
        assertThat(checklist.get("ready").asBoolean()).isTrue();
        assertThat(checklist.get("canApprove").asBoolean()).isTrue();

        checklist = data(post("/api/v1/onboardings/" + onboardingId + "/review", tokenA,
                "{\"version\":" + checklist.get("onboardingVersion").asLong() + "}", null));
        checklist = data(post("/api/v1/onboardings/" + onboardingId + "/approve", tokenA,
                "{\"version\":" + checklist.get("onboardingVersion").asLong() + "}", null));
        assertThat(checklist.get("onboardingStatus").asText()).isEqualTo("COMPLETED");
        assertThat(checklist.get("projectStatus").asText()).isEqualTo("READY");

        // V14 forward hardening: a completed onboarding is necessary but not sufficient for an arbitrary
        // project state to jump back into READY. READY is legal only from ONBOARDING.
        jdbc.update("UPDATE client_onboarding.projects SET status='DRAFT' WHERE organization_id=? AND id=?",
                fixture.orgA(), fixture.projectA());
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE client_onboarding.projects SET status='READY' WHERE organization_id=? AND id=?",
                fixture.orgA(), fixture.projectA())).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE client_onboarding.projects SET status='ONBOARDING' WHERE organization_id=? AND id=?",
                fixture.orgA(), fixture.projectA());
        jdbc.update("UPDATE client_onboarding.projects SET status='READY' WHERE organization_id=? AND id=?",
                fixture.orgA(), fixture.projectA());

        JsonNode activated = data(post("/api/v1/projects/" + fixture.projectA() + "/activate", tokenA,
                "{\"version\":" + checklist.get("projectVersion").asLong() + "}", null));
        assertThat(activated.get("status").asText()).isEqualTo("ACTIVE");

        UUID reviewId = jdbc.queryForObject(
                "SELECT id FROM client_onboarding.onboarding_reviews WHERE organization_id=? AND onboarding_id=? AND action='APPROVED'",
                UUID.class, fixture.orgA(), onboardingId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE client_onboarding.onboarding_reviews SET reason='tamper' WHERE organization_id=? AND id=?",
                fixture.orgA(), reviewId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishedVersionAndExistingOnboardingSnapshotAreImmutable() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        Workflow v1 = createPublishedWorkflow(token, "Versioned Workflow", simpleSteps("Original Name"));
        JsonNode onboarding = data(post("/api/v1/projects/" + fixture.projectA() + "/onboarding", token,
                "{\"templateVersionId\":\"" + v1.versionId() + "\",\"projectVersion\":0}", "phase3-version-0001"));
        UUID onboardingId = UUID.fromString(onboarding.get("id").asText());

        Response mutatePublished = put("/api/v1/workflow-templates/versions/" + v1.versionId(), token,
                "{\"version\":" + v1.entityVersion() + ",\"changeNote\":\"illegal\",\"steps\":[]}");
        assertThat(mutatePublished.status()).isEqualTo(409);

        JsonNode v2 = data(post("/api/v1/workflow-templates/" + v1.templateId() + "/versions", token, "{\"changeNote\":\"v2\"}", null));
        UUID v2Id = UUID.fromString(v2.get("id").asText());
        long v2EntityVersion = v2.get("version").asLong();
        data(put("/api/v1/workflow-templates/versions/" + v2Id, token,
                "{\"version\":" + v2EntityVersion + ",\"changeNote\":\"renamed\",\"steps\":" + simpleSteps("Changed Later") + "}"));

        JsonNode existing = data(get("/api/v1/onboardings/" + onboardingId, token));
        assertThat(existing.toString()).contains("Original Name").doesNotContain("Changed Later");

        UUID sourceStep = jdbc.queryForObject("SELECT id FROM client_onboarding.onboarding_template_steps WHERE organization_id=? AND template_version_id=? LIMIT 1",
                UUID.class, fixture.orgA(), v1.versionId());
        assertThatThrownBy(() -> jdbc.update("UPDATE client_onboarding.onboarding_template_steps SET name='DB Attack' WHERE organization_id=? AND id=?",
                fixture.orgA(), sourceStep)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void startIsIdempotentAndRejectsKeyReuseWithDifferentPayload() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        Workflow workflow = createPublishedWorkflow(token, "Idempotent Workflow", simpleSteps("Base"));
        String body = "{\"templateVersionId\":\"" + workflow.versionId() + "\",\"projectVersion\":0}";
        String key = "phase3-idempotency-0001";
        JsonNode first = data(post("/api/v1/projects/" + fixture.projectA() + "/onboarding", token, body, key));
        JsonNode retry = data(post("/api/v1/projects/" + fixture.projectA() + "/onboarding", token, body, key));
        assertThat(retry.get("id").asText()).isEqualTo(first.get("id").asText());

        Response reused = post("/api/v1/projects/" + fixture.projectA() + "/onboarding", token,
                "{\"templateVersionId\":\"" + workflow.versionId() + "\",\"projectVersion\":99}", key);
        assertThat(reused.status()).isEqualTo(409);
        assertThat(reused.body()).contains("IDEMPOTENCY_KEY_REUSED");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM client_onboarding.onboarding_instances WHERE organization_id=? AND project_id=?",
                Integer.class, fixture.orgA(), fixture.projectA());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void cycleValidationAndTenantActorForeignKeysFailClosed() throws Exception {
        String token = fixture.token(jdbc, jwtEncoder, securityProperties, fixture.managerA());
        JsonNode template = data(post("/api/v1/workflow-templates", token, "{\"name\":\"Cycle Test\"}", null));
        UUID versionId = UUID.fromString(template.get("versions").get(0).get("id").asText());
        long version = template.get("versions").get(0).get("version").asLong();
        String cyclic = """
                [{"stepKey":"A","name":"A","stepType":"MANUAL_TASK","displayOrder":0,"required":true,"blocking":true,"clientVisible":true,"requiresReview":false,"dependencyMode":"ALL","allowSkip":false,"allowReopen":false,"dependencyKeys":["B"],"configuration":{}},
                 {"stepKey":"B","name":"B","stepType":"MANUAL_TASK","displayOrder":1,"required":true,"blocking":true,"clientVisible":true,"requiresReview":false,"dependencyMode":"ALL","allowSkip":false,"allowReopen":false,"dependencyKeys":["A"],"configuration":{}}]
                """;
        Response response = put("/api/v1/workflow-templates/versions/" + versionId, token,
                "{\"version\":" + version + ",\"steps\":" + cyclic + "}");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("WORKFLOW_DEPENDENCY_CYCLE");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO client_onboarding.audit_logs
                    (id, organization_id, actor_user_id, action, entity_type, entity_id, source, request_id, correlation_id)
                VALUES (?, ?, ?, 'ATTACK', 'TEST', ?, 'API', 'req', 'corr')
                """, UUID.randomUUID(), fixture.orgA(), fixture.managerB().userId(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Workflow createPublishedWorkflow(String token, String name, String stepsJson) throws Exception {
        JsonNode template = data(post("/api/v1/workflow-templates", token,
                "{\"name\":\"" + name + "\",\"description\":\"Phase 3 integration workflow\"}", null));
        UUID templateId = UUID.fromString(template.get("id").asText());
        JsonNode draft = template.get("versions").get(0);
        UUID versionId = UUID.fromString(draft.get("id").asText());
        long version = draft.get("version").asLong();
        JsonNode saved = data(put("/api/v1/workflow-templates/versions/" + versionId, token,
                "{\"version\":" + version + ",\"changeNote\":\"validated\",\"steps\":" + stepsJson + "}"));
        JsonNode published = data(post("/api/v1/workflow-templates/versions/" + versionId + "/publish", token,
                "{\"version\":" + saved.get("version").asLong() + "}", null));
        return new Workflow(templateId, versionId, published.get("version").asLong());
    }

    private static String simpleSteps(String name) {
        return "[{\"stepKey\":\"BASE\",\"name\":\"" + name + "\",\"stepType\":\"MANUAL_TASK\",\"displayOrder\":0,"
                + "\"required\":true,\"blocking\":true,\"clientVisible\":true,\"requiresReview\":false,\"dependencyMode\":\"NONE\","
                + "\"allowSkip\":false,\"allowReopen\":false,\"dependencyKeys\":[],\"configuration\":{}}]";
    }

    private static String status(JsonNode onboarding, String key) {
        for (JsonNode step : onboarding.get("steps")) if (step.get("stepKey").asText().equals(key)) return step.get("status").asText();
        throw new AssertionError("Missing step " + key);
    }

    private static UUID stepId(JsonNode onboarding, String key) {
        for (JsonNode step : onboarding.get("steps")) if (step.get("stepKey").asText().equals(key)) return UUID.fromString(step.get("id").asText());
        throw new AssertionError("Missing step " + key);
    }

    private JsonNode data(Response response) throws Exception {
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return objectMapper.readTree(response.body()).get("data");
    }

    private Response get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET(); auth(builder, token); return send(builder.build());
    }

    private Response post(String path, String token, String json, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        auth(builder, token); return send(builder.build());
    }

    private Response put(String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).PUT(HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, "application/json"); auth(builder, token); return send(builder.build());
    }

    private void auth(HttpRequest.Builder builder, String token) { if (token != null) builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token); }
    private Response send(HttpRequest request) throws Exception { var response = http.send(request, HttpResponse.BodyHandlers.ofString()); return new Response(response.statusCode(), response.body()); }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private record Response(int status, String body) {}
    private record Workflow(UUID templateId, UUID versionId, long entityVersion) {}

    private record Fixture(UUID orgA, UUID orgB, Identity managerA, Identity managerB, Identity noPermission,
                           UUID clientA, UUID serviceA, UUID projectA) {
        static Fixture create(JdbcTemplate jdbc, PasswordEncoder encoder) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UUID orgA = UUID.randomUUID(), orgB = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)", orgA, "Phase3 A", "phase3-a-" + suffix);
            jdbc.update("INSERT INTO client_onboarding.organizations(id,name,slug) VALUES (?,?,?)", orgB, "Phase3 B", "phase3-b-" + suffix);
            Identity managerA = identity(jdbc, encoder, orgA, "phase3-a-" + suffix + "@example.com", true);
            Identity managerB = identity(jdbc, encoder, orgB, "phase3-b-" + suffix + "@example.com", true);
            Identity none = identity(jdbc, encoder, orgA, "phase3-none-" + suffix + "@example.com", false);
            List<String> permissions = List.of("CLIENT_READ", "SERVICE_READ", "PROJECT_READ", "PROJECT_UPDATE", "PROJECT_ACTIVATE", "WORKFLOW_READ", "WORKFLOW_MANAGE", "ONBOARDING_START", "ONBOARDING_READ", "ONBOARDING_REVIEW", "ONBOARDING_APPROVE");
            UUID roleA = role(jdbc, orgA, managerA.userId(), "PHASE3_A_" + suffix, permissions);
            UUID roleB = role(jdbc, orgB, managerB.userId(), "PHASE3_B_" + suffix, permissions);
            assign(jdbc, orgA, managerA.memberId(), roleA, managerA.userId());
            assign(jdbc, orgB, managerB.memberId(), roleB, managerB.userId());
            UUID clientA = UUID.randomUUID(), serviceA = UUID.randomUUID(), projectA = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.clients(id,organization_id,name,status,created_by,updated_by) VALUES (?,?,?,'ACTIVE',?,?)", clientA, orgA, "Workflow Client", managerA.userId(), managerA.userId());
            jdbc.update("INSERT INTO client_onboarding.services(id,organization_id,code,name,created_by,updated_by) VALUES (?,?,?,?,?,?)", serviceA, orgA, "META_ADS", "Meta Ads", managerA.userId(), managerA.userId());
            jdbc.update("INSERT INTO client_onboarding.projects(id,organization_id,client_id,service_id,name,created_by,updated_by) VALUES (?,?,?,?,?,?,?)", projectA, orgA, clientA, serviceA, "Workflow Project", managerA.userId(), managerA.userId());
            return new Fixture(orgA, orgB, managerA, managerB, none, clientA, serviceA, projectA);
        }

        String token(JdbcTemplate jdbc, JwtEncoder encoder, SecurityProperties props, Identity identity) {
            UUID org = jdbc.queryForObject("SELECT organization_id FROM client_onboarding.organization_users WHERE id=?", UUID.class, identity.memberId());
            UUID sessionId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO client_onboarding.auth_sessions
                      (id, organization_id, user_id, organization_user_id, refresh_token_hash, credentials_version, expires_at)
                    VALUES (?, ?, ?, ?, ?, 0, ?)
                    """, sessionId, org, identity.userId(), identity.memberId(), CryptoSupport.sha256Hex(UUID.randomUUID().toString()), Instant.now().plusSeconds(3600));
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer(props.tokenIssuer()).issuedAt(now).expiresAt(now.plusSeconds(900))
                    .subject(identity.userId().toString()).audience(List.of(props.tokenAudience())).claim("org", org.toString())
                    .claim("mid", identity.memberId().toString()).claim("sid", sessionId.toString()).claim("cv", 0L).build();
            return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        }

        private static Identity identity(JdbcTemplate jdbc, PasswordEncoder encoder, UUID org, String email, boolean mfa) {
            UUID user = UUID.randomUUID(), member = UUID.randomUUID(); Instant now = Instant.now();
            jdbc.update("""
                    INSERT INTO client_onboarding.users
                      (id,email,normalized_email,display_name,password_hash,email_verified_at,mfa_enabled,mfa_secret_encrypted)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, user, email, email, "Phase3 User", encoder.encode("Velvet-River-84-Comet"), now, mfa, mfa ? "test-secret" : null);
            jdbc.update("INSERT INTO client_onboarding.organization_users(id,organization_id,user_id,status,joined_at) VALUES (?,?,?,'ACTIVE',?)", member, org, user, now);
            return new Identity(user, member);
        }

        private static UUID role(JdbcTemplate jdbc, UUID org, UUID actor, String code, List<String> permissions) {
            UUID role = UUID.randomUUID();
            jdbc.update("INSERT INTO client_onboarding.roles(id,organization_id,code,name,system_role,created_by,updated_by) VALUES (?,?,?,?,false,?,?)", role, org, code, code, actor, actor);
            for (String permission : permissions) {
                UUID permissionId = jdbc.queryForObject("SELECT id FROM client_onboarding.permissions WHERE code=?", UUID.class, permission);
                jdbc.update("INSERT INTO client_onboarding.role_permissions(organization_id,role_id,permission_id,created_by) VALUES (?,?,?,?)", org, role, permissionId, actor);
            }
            return role;
        }

        private static void assign(JdbcTemplate jdbc, UUID org, UUID member, UUID role, UUID actor) {
            jdbc.update("INSERT INTO client_onboarding.organization_user_roles(organization_id,organization_user_id,role_id,created_by) VALUES (?,?,?,?)", org, member, role, actor);
        }
    }

    private record Identity(UUID userId, UUID memberId) {}
}
