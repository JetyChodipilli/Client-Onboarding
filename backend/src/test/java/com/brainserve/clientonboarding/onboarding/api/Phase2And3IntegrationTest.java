package com.brainserve.clientonboarding.onboarding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainserve.clientonboarding.auth.api.TestSecurityNotificationConfiguration;
import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityNotificationConfiguration.class)
class Phase2And3IntegrationTest {
    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "CLIENT_CREATE", "CLIENT_READ", "CLIENT_UPDATE", "PROJECT_CREATE", "PROJECT_READ",
            "PROJECT_UPDATE", "SERVICE_MANAGE", "WORKFLOW_READ", "WORKFLOW_MANAGE",
            "ONBOARDING_START", "ONBOARDING_REVIEW");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthRepository auth;
    @Autowired SecureTokenService tokens;
    @Autowired Clock clock;
    @Autowired DataSource dataSource;

    private UUID organizationA;
    private UUID organizationB;
    private Cookie managerA;
    private Cookie managerB;
    private Cookie deniedA;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(dataSource).schemas("app").defaultSchema("app")
                .createSchemas(true).locations("classpath:db/migration").load().migrate();
        clearData();
        organizationA = organization("phase23-a", "Phase 2 and 3 A");
        organizationB = organization("phase23-b", "Phase 2 and 3 B");
        managerA = session(organizationA, "manager-a@example.test", "Manager A", MANAGER_PERMISSIONS, true);
        managerB = session(organizationB, "manager-b@example.test", "Manager B", MANAGER_PERMISSIONS, true);
        deniedA = session(organizationA, "denied-a@example.test", "Denied A", Set.of(), false);
    }

    @Test
    void phase2ApisEnforcePermissionsTenancyPrimaryContactsAndRelationshipImmutability() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clients").cookie(deniedA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        UUID serviceId = id(createService(managerA, "META_ADS", "Meta Ads"), "/data/id");
        UUID clientId = id(createClient(managerA, "Acme Studio"), "/data/id");
        MvcResult contactResult = mockMvc.perform(post("/api/v1/clients/{id}/contacts", clientId)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("name", "Ada Client", "email", "ada@acme.test",
                                "primary", true, "version", 0))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.primary").value(true)).andReturn();
        UUID contactId = id(contactResult, "/data/id");

        mockMvc.perform(patch("/api/v1/client-contacts/{id}", contactId).cookie(managerA).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "Ada Client", "email", "ada@acme.test",
                                "primary", false, "version", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.primary").value(false));
        mockMvc.perform(get("/api/v1/clients/{id}/contacts", clientId).cookie(managerA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].primary").value(false));

        UUID projectId = id(createProject(managerA, clientId, serviceId, "Acme launch"), "/data/id");
        mockMvc.perform(get("/api/v1/clients/{id}", clientId).cookie(managerB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/{id}", projectId).cookie(managerB))
                .andExpect(status().isNotFound());

        jdbc.sql("UPDATE projects SET status = 'ONBOARDING' WHERE organization_id = :org AND id = :id")
                .param("org", organizationA).param("id", projectId).update();
        UUID otherClient = id(createClient(managerA, "Other Client"), "/data/id");
        mockMvc.perform(patch("/api/v1/projects/{id}", projectId).cookie(managerA).with(csrf())
                        .contentType("application/json")
                        .content(json(projectBody(otherClient, serviceId, "Acme launch", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_RELATIONSHIP_IMMUTABLE"));
    }

    @Test
    void phase3SnapshotsPublishedTemplatesAndEvaluatesDependenciesWithoutTenantEscape() throws Exception {
        UUID serviceId = id(createService(managerA, "META_ADS", "Meta Ads"), "/data/id");
        UUID clientId = id(createClient(managerA, "Workflow Client"), "/data/id");
        UUID projectId = id(createProject(managerA, clientId, serviceId, "Workflow launch"), "/data/id");

        MvcResult created = mockMvc.perform(post("/api/v1/workflow-templates").cookie(managerA).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "Meta Ads Onboarding", "description", "Versioned flow",
                                "serviceId", serviceId))))
                .andExpect(status().isCreated()).andReturn();
        UUID templateId = id(created, "/data/template/id");
        UUID versionId = id(created, "/data/draftVersion/id");
        UUID firstId = UUID.randomUUID();
        UUID disabledId = UUID.randomUUID();
        UUID allId = UUID.randomUUID();
        UUID anyId = UUID.randomUUID();
        List<Map<String, Object>> steps = List.of(
                step(firstId, "BRIEF", "Collect brief", 0, "NONE", List.of(), true, true, null),
                step(disabledId, "LEGAL", "Legal review", 1, "NONE", List.of(), true, true,
                        Map.of("field", "SERVICE_CODE", "operator", "EQUALS", "value", "OTHER")),
                step(allId, "ALL_NEXT", "All dependency", 2, "ALL", List.of(firstId), false, false, null),
                step(anyId, "ANY_NEXT", "Any dependency", 3, "ANY", List.of(firstId, disabledId), false, false, null));

        mockMvc.perform(put("/api/v1/workflow-template-versions/{id}/steps", versionId)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("version", 0, "steps", steps))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.steps", hasSize(4)));
        mockMvc.perform(post("/api/v1/workflow-template-versions/{id}/publish", versionId)
                        .queryParam("version", "1").cookie(managerA).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version.status").value("PUBLISHED"));

        MvcResult started = mockMvc.perform(post("/api/v1/projects/{id}/onboarding", projectId)
                        .header("Idempotency-Key", "start-workflow-0001")
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("templateVersionId", versionId, "projectVersion", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.onboarding.ready").value(false))
                .andExpect(jsonPath("$.data.steps[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.steps[1].applicable").value(false))
                .andExpect(jsonPath("$.data.steps[2].status").value("LOCKED"))
                .andExpect(jsonPath("$.data.steps[3].status").value("LOCKED"))
                .andReturn();
        UUID onboardingId = id(started, "/data/onboarding/id");
        UUID firstInstanceId = id(started, "/data/steps/0/id");

        Cookie reviewerOnly = session(organizationA, "reviewer-a@example.test", "Reviewer A",
                Set.of("WORKFLOW_READ", "ONBOARDING_REVIEW"), true);
        mockMvc.perform(post("/api/v1/onboarding-steps/{id}/transition", firstInstanceId)
                        .cookie(reviewerOnly).with(csrf()).contentType("application/json")
                        .content(json(Map.of("targetStatus", "IN_PROGRESS", "version", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{id}/onboarding", projectId)
                        .header("Idempotency-Key", "start-workflow-0001")
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("templateVersionId", versionId, "projectVersion", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.onboarding.id").value(onboardingId.toString()));
        mockMvc.perform(post("/api/v1/projects/{id}/onboarding", projectId)
                        .header("Idempotency-Key", "start-workflow-0001")
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("templateVersionId", versionId, "projectVersion", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(post("/api/v1/onboarding-steps/{id}/transition", firstInstanceId)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("targetStatus", "COMPLETED", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.ready").value(true))
                .andExpect(jsonPath("$.data.steps[2].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.steps[3].status").value("AVAILABLE"));

        MvcResult copied = mockMvc.perform(post("/api/v1/workflow-templates/{id}/versions", templateId)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("sourceVersionId", versionId))))
                .andExpect(status().isCreated()).andReturn();
        UUID copiedVersionId = id(copied, "/data/version/id");
        JsonNode copiedSteps = tree(copied).at("/data/steps");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edited = json.convertValue(copiedSteps, List.class);
        edited.get(0).put("name", "Changed in version 2");
        mockMvc.perform(put("/api/v1/workflow-template-versions/{id}/steps", copiedVersionId)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("version", 1, "steps", edited))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/onboardings/{id}", onboardingId).cookie(managerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].name").value("Collect brief"));
        mockMvc.perform(put("/api/v1/workflow-template-versions/{id}/steps", versionId)
                        .cookie(managerA).with(csrf()).contentType("application/json")
                        .content(json(Map.of("version", 2, "steps", steps))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_VERSION_IMMUTABLE"));

        mockMvc.perform(get("/api/v1/onboardings/{id}", onboardingId).cookie(managerB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/workflow-templates").cookie(deniedA))
                .andExpect(status().isForbidden());
    }

    private MvcResult createService(Cookie cookie, String code, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/services").cookie(cookie).with(csrf())
                        .contentType("application/json")
                        .content(json(Map.of("code", code, "name", name, "description", "Service",
                                "status", "ACTIVE", "version", 0))))
                .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createClient(Cookie cookie, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name); body.put("status", "ACTIVE"); body.put("email", "ops@client.test");
        body.put("version", 0);
        return mockMvc.perform(post("/api/v1/clients").cookie(cookie).with(csrf())
                        .contentType("application/json").content(json(body)))
                .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createProject(Cookie cookie, UUID clientId, UUID serviceId, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/projects").cookie(cookie).with(csrf())
                        .contentType("application/json").content(json(projectBody(clientId, serviceId, name, 0))))
                .andExpect(status().isCreated()).andReturn();
    }

    private Map<String, Object> projectBody(UUID clientId, UUID serviceId, String name, long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId); body.put("serviceId", serviceId); body.put("name", name);
        body.put("description", "Project"); body.put("valueMinor", 750000); body.put("currencyCode", "USD");
        body.put("version", version);
        return body;
    }

    private Map<String, Object> step(UUID id, String key, String name, int order, String dependencyMode,
                                     List<UUID> dependencies, boolean required, boolean blocking,
                                     Map<String, String> condition) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("stepKey", key); value.put("name", name);
        value.put("stepType", "MANUAL_TASK"); value.put("displayOrder", order);
        value.put("required", required); value.put("blocking", blocking); value.put("clientVisible", true);
        value.put("requiresReview", false); value.put("dependencyMode", dependencyMode);
        value.put("condition", condition); value.put("dueAfterHours", 24); value.put("allowSkip", !blocking);
        value.put("allowReopen", true); value.put("configuration", Map.of());
        value.put("dependencyStepIds", dependencies);
        return value;
    }

    private UUID id(MvcResult result, String pointer) throws Exception {
        return UUID.fromString(tree(result).at(pointer).asText());
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private String json(Object value) throws Exception { return json.writeValueAsString(value); }

    private UUID organization(String slug, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO organizations (id, slug, name, status, created_at, updated_at, version)
                VALUES (:id, :slug, :name, 'ACTIVE', :now, :now, 0)
                """).param("id", id).param("slug", slug).param("name", name)
                .param("now", Instant.now()).update();
        return id;
    }

    private Cookie session(UUID organizationId, String email, String name, Set<String> permissions,
                           boolean mfaVerified) {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.sql("""
                INSERT INTO users (id, email, display_name, password_hash, principal_type, status,
                    email_verified_at, failed_login_count, credential_version, created_at, updated_at, version)
                VALUES (:id, :email, :name, :password, 'INTERNAL', 'ACTIVE', :now, 0, 0, :now, :now, 0)
                """).param("id", userId).param("email", email).param("name", name)
                .param("password", passwordEncoder.encode("Integration7Password"))
                .param("now", now).update();
        jdbc.sql("""
                INSERT INTO roles (id, organization_id, name, description, created_at, updated_at, version)
                VALUES (:id, :org, :name, '', :now, :now, 0)
                """).param("id", roleId).param("org", organizationId).param("name", "Role " + name)
                .param("now", now).update();
        for (String permission : permissions) {
            jdbc.sql("""
                    INSERT INTO role_permissions (role_id, permission_id, created_at)
                    SELECT :role, id, :now FROM permissions WHERE code = :code
                    """).param("role", roleId).param("now", now).param("code", permission).update();
        }
        jdbc.sql("""
                INSERT INTO organization_users (id, organization_id, user_id, role_id, status,
                    invited_at, joined_at, created_at, updated_at, version)
                VALUES (:id, :org, :user, :role, 'ACTIVE', :now, :now, :now, :now, 0)
                """).param("id", membershipId).param("org", organizationId).param("user", userId)
                .param("role", roleId).param("now", now).update();
        String raw = tokens.issue();
        auth.insertSession(new AuthSession(UUID.randomUUID(), organizationId, userId, tokens.hash(raw), 0,
                now, now, now.plusSeconds(3600), null, mfaVerified ? now : null), "ip", "agent");
        return new Cookie("BOS_SESSION", raw);
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
}
