package com.brainserve.clientonboarding.assets.api;

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
@Import({TestSecurityNotificationConfiguration.class,Phase6IntegrationTest.StorageConfiguration.class})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class Phase6IntegrationTest {
    static final EmbeddedPostgres PG=start();
    static EmbeddedPostgres start() { try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories","").start(); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:postgresql://localhost:"+PG.getPort()+"/postgres?currentSchema=app");
        p.add("spring.datasource.username",()->"postgres");p.add("spring.datasource.password",()->"");p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @AfterAll static void close() throws java.io.IOException { PG.close(); }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcClient jdbc;
    @Autowired SecureTokenService tokens; @Autowired AuthRepository auth;
    UUID org,foreignOrg; String project,step,requirement,onboarding,portalPath,slug;
    Cookie admin,foreign,client,reader;

    @BeforeEach void setup() throws Exception {
        slug="forms-"+UUID.randomUUID();org=organization(slug);foreignOrg=organization("other-"+UUID.randomUUID());
        var permissions=Set.of("CLIENT_CREATE","CLIENT_UPDATE","CLIENT_READ","PROJECT_CREATE","PROJECT_READ","PROJECT_UPDATE","SERVICE_MANAGE","WORKFLOW_MANAGE","WORKFLOW_READ","ONBOARDING_START","ONBOARDING_INVITE","ONBOARDING_REVIEW","ASSET_READ","ASSET_MANAGE","ASSET_REVIEW");
        admin=internal(org,permissions);foreign=internal(foreignOrg,permissions);reader=internal(org,Set.of("ASSET_READ"));
        store.reset();
        scanner.started=null;scanner.resume=null;
        requirement=data(postJson("/asset-requirements",admin,Map.of("name","Brand file","instructions","Provide a safe brand document.","allowedMimes",List.of("text/plain","application/pdf"),"maxBytes",4096)).andExpect(status().isCreated())).get("id").asText();
        String company=data(postJson("/clients",admin,Map.of("name","Client","status","ACTIVE","version",0)).andExpect(status().isCreated())).get("id").asText();
        String contact=data(postJson("/clients/"+company+"/contacts",admin,Map.of("name","Ada","email","client-"+org+"@example.test","primary",true,"version",0)).andExpect(status().isCreated())).get("id").asText();
        String service=data(postJson("/services",admin,Map.of("code","WEB","name","Web","status","ACTIVE","version",0)).andExpect(status().isCreated())).get("id").asText();
        project=data(postJson("/projects",admin,Map.of("clientId",company,"serviceId",service,"name","Launch","version",0)).andExpect(status().isCreated())).get("id").asText();
        var wf=data(postJson("/workflow-templates",admin,Map.of("name","Launch","serviceId",service)).andExpect(status().isCreated()));
        String wv=wf.at("/draftVersion/id").asText();
        Map<String,Object> spec=new java.util.HashMap<>();
        spec.put("id",UUID.randomUUID().toString());spec.put("stepKey","BRIEF");spec.put("name","Launch questionnaire");spec.put("stepType","FILE_UPLOAD");spec.put("displayOrder",0);spec.put("required",true);spec.put("blocking",true);spec.put("clientVisible",true);spec.put("requiresReview",true);spec.put("dependencyMode","NONE");spec.put("allowSkip",false);spec.put("allowReopen",false);spec.put("configuration",Map.of("assetRequirementId",requirement));spec.put("dependencyStepIds",List.of());
        putJson("/workflow-template-versions/"+wv+"/steps",admin,Map.of("version",0,"steps",List.of(spec))).andExpect(status().isOk());
        postJson("/workflow-template-versions/"+wv+"/publish?version=1",admin,Map.of()).andExpect(status().isOk());
        var started=data(postJson("/projects/"+project+"/onboarding",admin,Map.of("templateVersionId",wv,"projectVersion",0)).andExpect(status().isCreated()));
        step=started.at("/steps/0/id").asText();onboarding=started.at("/onboarding/id").asText();
        String invitation=data(postJson("/onboardings/"+onboarding+"/client-invitations",admin,Map.of("contactId",contact,"role","MEMBER")).andExpect(status().isCreated())).get("id").asText();
        String raw=tokens.issue();jdbc.sql("UPDATE client_invitations SET token_hash=? WHERE organization_id=? AND id=?").params(tokens.hash(raw),org,UUID.fromString(invitation)).update();
        postJson("/client-invitations/accept",null,Map.of("token",raw,"password","ClientForm7Password")).andExpect(status().isOk());
        var login=postJson("/client-auth/login",null,Map.of("organizationSlug",slug,"email","client-"+org+"@example.test","password","ClientForm7Password")).andExpect(status().isOk()).andReturn();
        String cookie=login.getResponse().getHeader("Set-Cookie");client=new Cookie("BOS_SESSION",cookie.substring("BOS_SESSION=".length(),cookie.indexOf(';')));
        portalPath="/client-portal/projects/"+project+"/assets/"+step;
    }
    @Autowired FakeStorage store;
    @Autowired FakeScanner scanner;
    @Test void scanReviewRevisionReplacementApprovalAndHistory() throws Exception {
        scanner.fail=false;scanner.infected=false;
        upload(0,"First safe file").andExpect(status().isOk()).andExpect(jsonPath("$.data.asset.version").value(1));
        String first=data(getJson(portalPath,client)).at("/current/id").asText();
        postJson(portalPath+"/versions/"+first+"/download",client,Map.of()).andExpect(status().isConflict());
        submit(1).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.status").value("SUBMITTED"));
        review(3,"UNDER_REVIEW","").andExpect(status().isOk());
        review(4,"NEEDS_REVISION","").andExpect(status().isBadRequest());
        review(4,"NEEDS_REVISION","Please use the final brand guide.").andExpect(status().isOk());
        upload(5,"Final safe file").andExpect(status().isOk());
        submit(6).andExpect(status().isOk());
        review(8,"APPROVED","Confirmed").andExpect(status().isOk()).andExpect(jsonPath("$.data.stepStatus").value("COMPLETED"));
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(true));
        getJson(portalPath+"/versions",client).andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.data[1].file.status").value("REPLACED")).andExpect(jsonPath("$.data[1].reviews.length()").value(2));
        postJson(portalPath+"/versions/"+first+"/download",client,Map.of()).andExpect(status().isOk());
        assertThat(store.downloadedVersion).isEqualTo("version-1");
        postJson("/asset-responses/"+step+"/versions/"+first+"/download",foreign,Map.of()).andExpect(status().isNotFound());
        postJson("/client-portal/projects/"+UUID.randomUUID()+"/assets/"+step+"/versions/"+first+"/download",client,Map.of()).andExpect(status().isNotFound());
        assertThatThrownBy(()->jdbc.sql("UPDATE asset_versions SET object_version_id='tampered' WHERE organization_id=?").param(org).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("DELETE FROM asset_reviews WHERE organization_id=?").param(org).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM asset_outbox_events WHERE organization_id=? AND event_type='ASSET_APPROVED'").param(org).query(Long.class).single()).isEqualTo(1);
        assertThat(data(getJson(portalPath,client)).toString()).doesNotContain("objectKey","objectVersionId","scanLeaseId","sha256");
    }
    @Test void scannerFailureIsRetryableAndQuarantineNeverAllowsDownloadOrReview() throws Exception {
        scanner.fail=true;scanner.infected=false;
        upload(0,"Retry safe file").andExpect(status().isOk());
        submit(1).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.scanStatus").value("ERROR")).andExpect(jsonPath("$.data.stepStatus").value("IN_PROGRESS"));
        scanner.fail=false;scanner.infected=true;
        submit(3).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.status").value("QUARANTINED"));
        String id=data(getJson(portalPath,client)).at("/current/id").asText();
        postJson(portalPath+"/versions/"+id+"/download",admin,Map.of()).andExpect(status().isForbidden());
        postJson("/asset-responses/"+step+"/versions/"+id+"/download",admin,Map.of()).andExpect(status().isConflict());
        review(5,"APPROVED","Override").andExpect(status().isConflict());
        scanner.infected=false;
        upload(5,"Replacement file").andExpect(status().isOk());submit(6).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.scanStatus").value("CLEAN"));
    }
    @Test void tenantPermissionsProjectGrantsCsrfAndGenericTransitionsAreEnforced() throws Exception {
        getJson("/asset-responses/"+step,foreign).andExpect(status().isNotFound());
        getJson("/asset-requirements",null).andExpect(status().isUnauthorized());
        getJson("/asset-requirements",client).andExpect(status().isForbidden());
        postJson("/asset-requirements",reader,Map.of("name","Denied","allowedMimes",List.of("text/plain"),"maxBytes",100)).andExpect(status().isForbidden());
        postJson("/asset-requirements/"+requirement+"/archive?version=0",foreign,Map.of()).andExpect(status().isNotFound());
        postJson("/asset-responses/"+step+"/review",reader,Map.of("version",0,"decision","APPROVED")).andExpect(status().isForbidden());
        postJson("/asset-responses/"+step+"/review",client,Map.of("version",0,"decision","APPROVED")).andExpect(status().isForbidden());
        getJson("/client-portal/projects/"+UUID.randomUUID()+"/assets/"+step,client).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1"+portalPath+"/submit").cookie(client).contentType("application/json").content(json.writeValueAsString(Map.of("version",0)))).andExpect(status().isForbidden());
        postJson("/onboarding-steps/"+step+"/transition",admin,Map.of("version",0,"targetStatus","COMPLETED")).andExpect(status().isConflict());
        postJson("/client-portal/projects/"+project+"/steps/"+step+"/transition",client,Map.of("version",0,"targetStatus","COMPLETED")).andExpect(status().isConflict());
        jdbc.sql("UPDATE onboarding_step_instances SET assigned_role='CLIENT_ADMIN' WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        getJson(portalPath,client).andExpect(status().isNotFound());
    }
    @Test void invalidContentAndHashRemainUnavailable() throws Exception {
        scanner.fail=false;scanner.infected=false;
        upload(0,"Safe file").andExpect(status().isOk());store.body="Changed file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        submit(1).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.status").value("REJECTED"));
        upload(3,"<html><script>alert(1)</script></html>").andExpect(status().isOk());
        submit(4).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.status").value("REJECTED"));
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        getJson(portalPath+"/versions?size=51",client).andExpect(status().isBadRequest());
    }
    @Test void staleConcurrentSubmissionsAndHeldProjectsCannotMutate() throws Exception {
        scanner.fail=false;scanner.infected=false;upload(0,"Concurrent safe file").andExpect(status().isOk());
        upload(0,"Stale overwrite").andExpect(status().isConflict());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> action=()->{gate.await();return submit(1).andReturn().getResponse().getStatus();};
            var a=pool.submit(action);var b=pool.submit(action);gate.countDown();
            assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }finally{pool.shutdownNow();}
        for(String state:List.of("ON_HOLD","CANCELLED")){
            jdbc.sql("UPDATE projects SET status=? WHERE organization_id=? AND id=?").params(state,org,UUID.fromString(project)).update();
            review(3,"APPROVED","Confirmed").andExpect(status().isConflict());
        }
        assertThat(jdbc.sql("SELECT count(*) FROM asset_versions WHERE organization_id=?").param(org).query(Long.class).single()).isEqualTo(1);
    }
    @Test void expiredLeaseRetriesPinnedObjectAndRequirementsStayImmutable() throws Exception {
        scanner.fail=true;scanner.infected=false;upload(0,"Pinned safe file").andExpect(status().isOk());submit(1).andExpect(status().isOk());
        assertThatThrownBy(()->jdbc.sql("UPDATE asset_requirements SET max_bytes=100 WHERE organization_id=?").param(org).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        postJson("/asset-requirements/"+requirement+"/archive?version=0",admin,Map.of()).andExpect(status().isOk());
        jdbc.sql("UPDATE asset_versions SET status='SCANNING',scan_status='SCANNING',scan_lease_id=?,scan_lease_until=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE organization_id=?").params(UUID.randomUUID(),org).update();
        scanner.fail=false;submit(3).andExpect(status().isOk()).andExpect(jsonPath("$.data.current.status").value("SUBMITTED"));
        assertThat(store.inspectedVersion).isEqualTo("version-1");
    }
    @Test void projectHoldDuringScanPreventsCompletionAndDownload() throws Exception {
        scanner.fail=false;scanner.infected=false;upload(0,"Safe but held file").andExpect(status().isOk());
        scanner.started=new java.util.concurrent.CountDownLatch(1);scanner.resume=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result=pool.submit(()->submit(1).andReturn().getResponse().getStatus());
            assertThat(scanner.started.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            jdbc.sql("UPDATE projects SET status='ON_HOLD' WHERE organization_id=? AND id=?").params(org,UUID.fromString(project)).update();
            scanner.resume.countDown();
            assertThat(result.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
            String id=data(getJson(portalPath,client)).at("/current/id").asText();
            postJson(portalPath+"/versions/"+id+"/download",client,Map.of()).andExpect(status().isConflict());
            getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
            assertThat(jdbc.sql("SELECT count(*) FROM asset_outbox_events WHERE organization_id=? AND event_type IN ('ASSET_UPLOADED','ASSET_APPROVED')").param(org).query(Long.class).single()).isZero();
        }finally{scanner.resume.countDown();pool.shutdownNow();}
    }
    @Test void noReviewAutoCompletesOnlyAfterSafeScanAndSkipReopenHonorRules() throws Exception {
        scanner.fail=false;scanner.infected=false;
        postJson("/asset-responses/"+step+"/skip",admin,Map.of("version",0,"note","Skip")).andExpect(status().isConflict());
        jdbc.sql("UPDATE onboarding_step_instances SET requires_review=FALSE,allow_reopen=TRUE WHERE organization_id=? AND id=?").params(org,UUID.fromString(step)).update();
        upload(0,"Auto approved safe file").andExpect(status().isOk());submit(1).andExpect(jsonPath("$.data.stepStatus").value("COMPLETED"));
        postJson("/asset-responses/"+step+"/reopen",admin,Map.of("version",3,"note","Please update branding")).andExpect(status().isOk()).andExpect(jsonPath("$.data.stepStatus").value("NEEDS_REVISION"));
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        upload(4,"New branding").andExpect(status().isOk());submit(5).andExpect(jsonPath("$.data.stepStatus").value("COMPLETED"));
    }
    ResultActions upload(long version,String value)throws Exception {
        store.body=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(store.body));
        return postJson(portalPath+"/upload-url",client,Map.of("version",version,"filename","../../brand.txt","mime","text/plain","byteSize",store.body.length,"sha256",hash));
    }
    ResultActions submit(long version)throws Exception{return postJson(portalPath+"/submit",client,Map.of("version",version));}
    @org.springframework.boot.test.context.TestConfiguration
    static class StorageConfiguration {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary FakeStorage fakeStorage(){return new FakeStorage();}
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary FakeScanner fakeScanner(){return new FakeScanner();}
    }
    static class FakeStorage implements com.brainserve.clientonboarding.assets.application.AssetStorage {
        byte[] body;long size;String mime,hash,inspectedVersion,downloadedVersion;
        void reset(){body=null;inspectedVersion=null;downloadedVersion=null;}
        public SignedUrl upload(String key,String mime,long size,String hash){this.size=size;this.mime=mime;this.hash=hash;return new SignedUrl("https://storage.example.test/upload","PUT",Map.of(),Instant.now().plusSeconds(600));}
        public StoredObject inspect(String key,String version){inspectedVersion=version;return new StoredObject("version-1",size,mime,hash,Instant.now());}
        public java.io.InputStream read(String key,String version){return new java.io.ByteArrayInputStream(body);}
        public SignedUrl download(String key,String version,String filename){downloadedVersion=version;return new SignedUrl("https://storage.example.test/download","GET",Map.of(),Instant.now().plusSeconds(60));}
    }
    static class FakeScanner implements com.brainserve.clientonboarding.assets.application.MalwareScanner {
        boolean fail,infected;
        java.util.concurrent.CountDownLatch started,resume;
        public Verdict scan(java.nio.file.Path file)throws java.io.IOException{
            if(started!=null){started.countDown();try{if(!resume.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("Test scan gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}}
            if(fail)throw new java.io.IOException("Scanner unavailable");return infected?Verdict.INFECTED:Verdict.CLEAN;
        }
    }
    ResultActions review(long version,String decision,String note) throws Exception { return postJson("/asset-responses/"+step+"/review",admin,Map.of("version",version,"decision",decision,"note",note)); }
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
