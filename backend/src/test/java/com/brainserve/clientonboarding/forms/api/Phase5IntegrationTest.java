package com.brainserve.clientonboarding.forms.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;
import com.brainserve.clientonboarding.auth.api.TestSecurityNotificationConfiguration;
import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(TestSecurityNotificationConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class Phase5IntegrationTest {
    static final EmbeddedPostgres PG=start();
    static EmbeddedPostgres start() { try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories","").start(); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:postgresql://localhost:"+PG.getPort()+"/postgres?currentSchema=app");
        p.add("spring.datasource.username",()->"postgres");p.add("spring.datasource.password",()->"");p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @AfterAll static void close() throws java.io.IOException { PG.close(); }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcClient jdbc;
    @Autowired SecureTokenService tokens; @Autowired AuthRepository auth;
    UUID org,foreignOrg; String project,step,form,definition,onboarding,portalPath,slug;
    Cookie admin,foreign,client,reader;

    @BeforeEach void setup() throws Exception {
        slug="forms-"+UUID.randomUUID();org=organization(slug);foreignOrg=organization("other-"+UUID.randomUUID());
        var permissions=Set.of("CLIENT_CREATE","CLIENT_UPDATE","CLIENT_READ","PROJECT_CREATE","PROJECT_READ","PROJECT_UPDATE","SERVICE_MANAGE","WORKFLOW_MANAGE","WORKFLOW_READ","ONBOARDING_START","ONBOARDING_INVITE","ONBOARDING_REVIEW","FORM_READ","FORM_MANAGE","FORM_REVIEW");
        admin=internal(org,permissions);foreign=internal(foreignOrg,permissions);reader=internal(org,Set.of("FORM_READ"));
        var created=data(postJson("/forms",admin,Map.of("name","Launch brief","description","Project requirements")).andExpect(status().isCreated()));
        form=created.at("/template/id").asText();definition=created.at("/definition/id").asText();
        var fields=List.of(Map.of("key","business","label","Business name","type","TEXT","required",true),Map.of("key","has_site","label","Existing website?","type","BOOLEAN","required",true),Map.of("key","website","label","Website URL","type","URL","required",true,"condition",Map.of("fieldKey","has_site","operator","EQUALS","value","true")));
        data(putJson("/form-versions/"+definition+"/fields",admin,Map.of("version",0,"fields",fields)).andExpect(status().isOk()));
        postJson("/form-versions/"+definition+"/publish?version=1",admin,Map.of()).andExpect(status().isOk());
        String company=data(postJson("/clients",admin,Map.of("name","Client","status","ACTIVE","version",0)).andExpect(status().isCreated())).get("id").asText();
        String contact=data(postJson("/clients/"+company+"/contacts",admin,Map.of("name","Ada","email","client-"+org+"@example.test","primary",true,"version",0)).andExpect(status().isCreated())).get("id").asText();
        String service=data(postJson("/services",admin,Map.of("code","WEB","name","Web","status","ACTIVE","version",0)).andExpect(status().isCreated())).get("id").asText();
        project=data(postJson("/projects",admin,Map.of("clientId",company,"serviceId",service,"name","Launch","version",0)).andExpect(status().isCreated())).get("id").asText();
        var wf=data(postJson("/workflow-templates",admin,Map.of("name","Launch","serviceId",service)).andExpect(status().isCreated()));
        String wv=wf.at("/draftVersion/id").asText();
        Map<String,Object> spec=new java.util.HashMap<>();
        spec.put("id",UUID.randomUUID().toString());spec.put("stepKey","BRIEF");spec.put("name","Launch questionnaire");spec.put("stepType","FORM");spec.put("displayOrder",0);spec.put("required",true);spec.put("blocking",true);spec.put("clientVisible",true);spec.put("requiresReview",true);spec.put("dependencyMode","NONE");spec.put("allowSkip",false);spec.put("allowReopen",false);spec.put("configuration",Map.of("formVersionId",definition));spec.put("dependencyStepIds",List.of());
        putJson("/workflow-template-versions/"+wv+"/steps",admin,Map.of("version",0,"steps",List.of(spec))).andExpect(status().isOk());
        postJson("/workflow-template-versions/"+wv+"/publish?version=1",admin,Map.of()).andExpect(status().isOk());
        var started=data(postJson("/projects/"+project+"/onboarding",admin,Map.of("templateVersionId",wv,"projectVersion",0)).andExpect(status().isCreated()));
        step=started.at("/steps/0/id").asText();onboarding=started.at("/onboarding/id").asText();
        String invitation=data(postJson("/onboardings/"+onboarding+"/client-invitations",admin,Map.of("contactId",contact,"role","MEMBER")).andExpect(status().isCreated())).get("id").asText();
        String raw=tokens.issue();jdbc.sql("UPDATE client_invitations SET token_hash=? WHERE organization_id=? AND id=?").params(tokens.hash(raw),org,UUID.fromString(invitation)).update();
        postJson("/client-invitations/accept",null,Map.of("token",raw,"password","ClientForm7Password")).andExpect(status().isOk());
        var login=postJson("/client-auth/login",null,Map.of("organizationSlug",slug,"email","client-"+org+"@example.test","password","ClientForm7Password")).andExpect(status().isOk()).andReturn();
        String cookie=login.getResponse().getHeader("Set-Cookie");client=new Cookie("BOS_SESSION",cookie.substring("BOS_SESSION=".length(),cookie.indexOf(';')));
        portalPath="/client-portal/projects/"+project+"/forms/"+step;
    }
    @Test void draftSubmitRevisionResubmitApprovalPreserveHistoryAndUpdateReadiness() throws Exception {
        putJson(portalPath+"/draft",client,Map.of("version",0,"answers",Map.of())).andExpect(status().isOk()).andExpect(jsonPath("$.data.response.version").value(1));
        postJson(portalPath+"/submit",client,Map.of("version",1,"answers",Map.of())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("business"));
        var answers=Map.of("business","First company","has_site",false,"website","hidden secret");
        postJson(portalPath+"/submit",client,Map.of("version",1,"answers",answers)).andExpect(status().isOk()).andExpect(jsonPath("$.data.response.status").value("SUBMITTED")).andExpect(jsonPath("$.data.response.answers.website").doesNotExist());
        putJson(portalPath+"/draft",client,Map.of("version",2,"answers",answers)).andExpect(status().isConflict());
        review(2,"UNDER_REVIEW","").andExpect(status().isOk());
        review(3,"NEEDS_REVISION","").andExpect(status().isBadRequest());
        review(3,"NEEDS_REVISION","Use your legal company name.").andExpect(status().isOk());
        putJson(portalPath+"/draft",client,Map.of("version",4,"answers",Map.of("business","Legal company","has_site",false))).andExpect(status().isOk());
        postJson(portalPath+"/submit",client,Map.of("version",5,"answers",Map.of("business","Legal company","has_site",false))).andExpect(status().isOk());
        review(6,"APPROVED","Confirmed").andExpect(status().isOk()).andExpect(jsonPath("$.data.stepStatus").value("COMPLETED"));
        getJson("/onboardings/"+onboarding,admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.onboarding.ready").value(true));
        getJson(portalPath+"/submissions",client).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.data[1].answers.business").value("First company")).andExpect(jsonPath("$.data[0].answers.business").value("Legal company"));
        assertThat(jdbc.sql("SELECT count(*) FROM form_outbox_events WHERE organization_id=?").param(org).query(Long.class).single()).isEqualTo(5);
        assertThat(jdbc.sql("SELECT after_state FROM audit_logs WHERE organization_id=? AND action='FORM_SUBMITTED'").param(org).query(String.class).list().toString()).doesNotContain("First company","Legal company");
    }
    @Test void tenantPermissionsGrantsCsrfAndGenericTransitionsCannotBypassForms() throws Exception {
        getJson("/forms/"+form,foreign).andExpect(status().isNotFound());
        putJson("/form-versions/"+definition+"/fields",foreign,Map.of("version",2,"fields",List.of())).andExpect(status().isBadRequest());
        postJson("/form-versions/"+definition+"/publish?version=2",foreign,Map.of()).andExpect(status().isNotFound());
        getJson("/form-responses/"+step,foreign).andExpect(status().isNotFound());
        getJson("/forms",client).andExpect(status().isForbidden());
        getJson("/forms",null).andExpect(status().isUnauthorized());
        postJson("/forms",reader,Map.of("name","Denied")).andExpect(status().isForbidden());
        postJson("/form-responses/"+step+"/review",reader,Map.of("version",0,"decision","APPROVED")).andExpect(status().isForbidden());
        getJson("/client-portal/projects/"+UUID.randomUUID()+"/forms/"+step,client).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1"+portalPath+"/draft").cookie(client).contentType("application/json").content("{\"version\":0,\"answers\":{}}" )).andExpect(status().isForbidden());
        postJson("/onboarding-steps/"+step+"/transition",admin,Map.of("version",0,"targetStatus","COMPLETED")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DEDICATED_STEP_FLOW_REQUIRED"));
        postJson("/client-portal/projects/"+project+"/steps/"+step+"/transition",client,Map.of("version",0,"targetStatus","COMPLETED")).andExpect(status().isConflict());
        jdbc.sql("UPDATE onboarding_step_instances SET assigned_role='CLIENT_ADMIN' WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        getJson(portalPath,client).andExpect(status().isNotFound());
        jdbc.sql("UPDATE onboarding_step_instances SET assigned_role=NULL,client_visible=FALSE WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        getJson(portalPath,client).andExpect(status().isNotFound());
    }
    @Test void snapshotsRemainPinnedAndDatabaseRejectsHistoryMutation() throws Exception {
        var draft=data(postJson("/forms/"+form+"/versions",admin,Map.of("sourceVersionId",definition)).andExpect(status().isCreated()));
        String next=draft.get("id").asText();
        putJson("/form-versions/"+next+"/fields",admin,Map.of("version",0,"fields",List.of(Map.of("key","new_question","label","New question","type","TEXT","required",true)))).andExpect(status().isOk());
        postJson("/form-versions/"+next+"/publish?version=1",admin,Map.of()).andExpect(status().isOk());
        getJson(portalPath,client).andExpect(status().isOk()).andExpect(jsonPath("$.data.definition.id").value(definition));
        postJson("/form-versions/"+definition+"/publish?version=2",admin,Map.of()).andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbc.sql("UPDATE form_versions SET fields_json='[]' WHERE organization_id=? AND id=?").params(org,UUID.fromString(definition)).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        postJson(portalPath+"/submit",client,Map.of("version",0,"answers",Map.of("business","Company","has_site",false))).andExpect(status().isOk());
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM form_submissions WHERE organization_id=?").param(org).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        postJson("/forms/"+form+"/archive?version=0",admin,Map.of()).andExpect(status().isOk());
        getJson(portalPath,client).andExpect(status().isOk());
    }
    @Test void staleWritesAndPausedLockedOrCancelledStepsRejectWithoutMutations() throws Exception {
        putJson(portalPath+"/draft",client,Map.of("version",0,"answers",Map.of("business","Keep me"))).andExpect(status().isOk());
        putJson(portalPath+"/draft",client,Map.of("version",0,"answers",Map.of("business","Overwrite"))).andExpect(status().isConflict());
        jdbc.sql("UPDATE onboarding_step_instances SET status='LOCKED' WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        putJson(portalPath+"/draft",client,Map.of("version",1,"answers",Map.of())).andExpect(status().isConflict());
        jdbc.sql("UPDATE onboarding_step_instances SET status='IN_PROGRESS' WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        for(String state:List.of("PAUSED","CANCELLED","COMPLETED")) {
            jdbc.sql("UPDATE onboarding_instances SET status=? WHERE organization_id=? AND id=?").params(state,org,UUID.fromString(onboarding)).update();
            postJson(portalPath+"/submit",client,Map.of("version",1,"answers",Map.of("business","Overwrite","has_site",false))).andExpect(status().isConflict());
        }
        getJson(portalPath,client).andExpect(status().isOk()).andExpect(jsonPath("$.data.response.answers.business").value("Keep me"));
    }
    @Test void concurrentReviewsProduceOneSuccessAndOneConflict() throws Exception {
        postJson(portalPath+"/submit",client,Map.of("version",0,"answers",Map.of("business","Company","has_site",false))).andExpect(status().isOk());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> action=()->{ gate.await(); return review(1,"APPROVED","Checked").andReturn().getResponse().getStatus(); };
            var a=pool.submit(action);var b=pool.submit(action);gate.countDown();
            assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
    }
    @Test void nonReviewFormsCompleteOnlyAfterValidatedSubmission() throws Exception {
        jdbc.sql("UPDATE onboarding_step_instances SET requires_review=FALSE WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        postJson(portalPath+"/submit",client,Map.of("version",0,"answers",Map.of("business","Company","has_site",true,"website","javascript:bad"))).andExpect(status().isBadRequest());
        postJson(portalPath+"/submit",client,Map.of("version",0,"answers",Map.of("business","Company","has_site",true,"website","https://example.test"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.response.status").value("APPROVED")).andExpect(jsonPath("$.data.stepStatus").value("COMPLETED"));
        review(1,"NEEDS_REVISION","Too late").andExpect(status().isConflict());
    }
    @Test void skipAndReopenRespectSnapshotRulesAndPreserveSubmissions() throws Exception {
        postJson("/form-responses/"+step+"/skip",admin,Map.of("version",0,"note","Not needed")).andExpect(status().isConflict());
        postJson(portalPath+"/submit",client,Map.of("version",0,"answers",Map.of("business","Company","has_site",false))).andExpect(status().isOk());
        review(1,"APPROVED","Confirmed").andExpect(status().isOk());
        postJson("/form-responses/"+step+"/reopen",admin,Map.of("version",2,"note","Changed brief")).andExpect(status().isConflict());
        jdbc.sql("UPDATE onboarding_step_instances SET allow_reopen=TRUE WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        postJson("/form-responses/"+step+"/reopen",admin,Map.of("version",2,"note","Changed brief")).andExpect(status().isOk()).andExpect(jsonPath("$.data.stepStatus").value("IN_PROGRESS"));
        getJson("/onboardings/"+onboarding,admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        getJson(portalPath+"/submissions",client).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        jdbc.sql("UPDATE onboarding_step_instances SET allow_skip=TRUE,blocking=FALSE WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        postJson("/form-responses/"+step+"/skip",admin,Map.of("version",3,"note","Scope changed")).andExpect(status().isOk()).andExpect(jsonPath("$.data.stepStatus").value("SKIPPED"));
        putJson(portalPath+"/draft",client,Map.of("version",4,"answers",Map.of())).andExpect(status().isConflict());
    }
    @Test void concurrentSubmissionsCreateExactlyOneSnapshotAndEvent() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> action=()->{ gate.await();return postJson(portalPath+"/submit",client,Map.of("version",0,"answers",Map.of("business","Concurrent","has_site",false))).andReturn().getResponse().getStatus(); };
            var a=pool.submit(action);var b=pool.submit(action);gate.countDown();
            assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.sql("SELECT count(*) FROM form_submissions WHERE organization_id=?").param(org).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM form_outbox_events WHERE organization_id=?").param(org).query(Long.class).single()).isEqualTo(1);
    }
    @Test void heldProjectsAndOversizedPagesRejectSafely() throws Exception {
        getJson("/forms?page=2147483647&size=100",admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        getJson(portalPath+"/submissions?size=21",client).andExpect(status().isBadRequest());
        for(String state:List.of("ON_HOLD","CANCELLED")) {
            jdbc.sql("UPDATE projects SET status=? WHERE organization_id=? AND id=?").params(state,org,UUID.fromString(project)).update();
            putJson(portalPath+"/draft",client,Map.of("version",0,"answers",Map.of())).andExpect(status().isConflict());
        }
        assertThat(jdbc.sql("SELECT count(*) FROM form_responses WHERE organization_id=?").param(org).query(Long.class).single()).isZero();
    }
    ResultActions review(long version,String decision,String note) throws Exception { return postJson("/form-responses/"+step+"/review",admin,Map.of("version",version,"decision",decision,"note",note)); }
    ResultActions postJson(String path,Cookie cookie,Object body) throws Exception { var r=post("/api/v1"+path).with(csrf()).contentType("application/json").content(json.writeValueAsString(body));if(cookie!=null)r.cookie(cookie);return mvc.perform(r); }
    ResultActions putJson(String path,Cookie cookie,Object body) throws Exception { return mvc.perform(put("/api/v1"+path).cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))); }
    ResultActions getJson(String path,Cookie cookie) throws Exception { var r=get("/api/v1"+path);if(cookie!=null)r.cookie(cookie);return mvc.perform(r); }
    JsonNode data(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsByteArray()).get("data"); }
    UUID organization(String slug) { UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO organizations(id,slug,name,status,created_at,updated_at,version) VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)").params(id,slug,"Forms agency").update();return id; }
    Cookie internal(UUID tenant,Set<String> permissions) {
        UUID user=UUID.randomUUID(),role=UUID.randomUUID();Instant now=Instant.now();
        jdbc.sql("INSERT INTO users(id,email,display_name,password_hash,principal_type,status,email_verified_at,failed_login_count,credential_version,created_at,updated_at,version) VALUES(?,?,?,'unused','INTERNAL','ACTIVE',?,0,0,?,?,0)").params(user,user+"@example.test","Reviewer",timestamp(now),timestamp(now),timestamp(now)).update();
        jdbc.sql("INSERT INTO roles(id,organization_id,name,description,created_at,updated_at,version) VALUES(?,?,?,'',?,?,0)").params(role,tenant,"Role "+role,timestamp(now),timestamp(now)).update();
        for(String code:permissions) jdbc.sql("INSERT INTO role_permissions(role_id,permission_id,created_at) SELECT ?,id,? FROM permissions WHERE code=?").params(role,timestamp(now),code).update();
        jdbc.sql("INSERT INTO organization_users(id,organization_id,user_id,role_id,status,invited_at,created_at,updated_at,version) VALUES(?,?,?,?,'ACTIVE',?,?,?,0)").params(UUID.randomUUID(),tenant,user,role,timestamp(now),timestamp(now),timestamp(now)).update();
        String raw=tokens.issue();auth.insertSession(new AuthSession(UUID.randomUUID(),tenant,user,tokens.hash(raw),0,now,now,now.plusSeconds(3600),null,now),"ip","agent");return new Cookie("BOS_SESSION",raw);
    }
}
