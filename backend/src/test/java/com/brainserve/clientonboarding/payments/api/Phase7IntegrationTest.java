package com.brainserve.clientonboarding.payments.api;

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
@Import({TestSecurityNotificationConfiguration.class,Phase7IntegrationTest.ProviderConfiguration.class})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class Phase7IntegrationTest {
    static final EmbeddedPostgres PG=start();
    static EmbeddedPostgres start() { try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories","").start(); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:postgresql://localhost:"+PG.getPort()+"/postgres?currentSchema=app");
        p.add("spring.datasource.username",()->"postgres");p.add("spring.datasource.password",()->"");p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @AfterAll static void close() throws java.io.IOException { PG.close(); }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcClient jdbc;
    @Autowired SecureTokenService tokens; @Autowired AuthRepository auth;
    UUID org,foreignOrg; String project,step,onboarding,portalPath,slug;
    Cookie admin,foreign,client,reader;

    @BeforeEach void setup() throws Exception {
        slug="forms-"+UUID.randomUUID();org=organization(slug);foreignOrg=organization("other-"+UUID.randomUUID());
        var permissions=Set.of("CLIENT_CREATE","CLIENT_UPDATE","CLIENT_READ","PROJECT_CREATE","PROJECT_READ","PROJECT_UPDATE","SERVICE_MANAGE","WORKFLOW_MANAGE","WORKFLOW_READ","ONBOARDING_START","ONBOARDING_INVITE","ONBOARDING_REVIEW","INVOICE_READ","INVOICE_CREATE","INVOICE_SEND","PAYMENT_OVERRIDE","PAYMENT_RECONCILE","PAYMENT_REFUND");
        admin=internal(org,permissions);foreign=internal(foreignOrg,permissions);reader=internal(org,Set.of("INVOICE_READ"));
        provider.reset();
        String company=data(postJson("/clients",admin,Map.of("name","Client","status","ACTIVE","version",0)).andExpect(status().isCreated())).get("id").asText();
        String contact=data(postJson("/clients/"+company+"/contacts",admin,Map.of("name","Ada","email","client-"+org+"@example.test","primary",true,"version",0)).andExpect(status().isCreated())).get("id").asText();
        String service=data(postJson("/services",admin,Map.of("code","WEB","name","Web","status","ACTIVE","version",0)).andExpect(status().isCreated())).get("id").asText();
        project=data(postJson("/projects",admin,Map.of("clientId",company,"serviceId",service,"name","Launch","version",0)).andExpect(status().isCreated())).get("id").asText();
        var wf=data(postJson("/workflow-templates",admin,Map.of("name","Launch","serviceId",service)).andExpect(status().isCreated()));
        String wv=wf.at("/draftVersion/id").asText();
        Map<String,Object> spec=new java.util.HashMap<>();
        spec.put("id",UUID.randomUUID().toString());spec.put("stepKey","BRIEF");spec.put("name","Launch questionnaire");spec.put("stepType","PAYMENT");spec.put("displayOrder",0);spec.put("required",true);spec.put("blocking",true);spec.put("clientVisible",true);spec.put("requiresReview",false);spec.put("dependencyMode","NONE");spec.put("allowSkip",false);spec.put("allowReopen",false);spec.put("configuration",Map.of("paymentPolicy","DEPOSIT","depositPercent",50));spec.put("dependencyStepIds",List.of());
        putJson("/workflow-template-versions/"+wv+"/steps",admin,Map.of("version",0,"steps",List.of(spec))).andExpect(status().isOk());
        postJson("/workflow-template-versions/"+wv+"/publish?version=1",admin,Map.of()).andExpect(status().isOk());
        var started=data(postJson("/projects/"+project+"/onboarding",admin,Map.of("templateVersionId",wv,"projectVersion",0)).andExpect(status().isCreated()));
        step=started.at("/steps/0/id").asText();onboarding=started.at("/onboarding/id").asText();
        String invitation=data(postJson("/onboardings/"+onboarding+"/client-invitations",admin,Map.of("contactId",contact,"role","MEMBER")).andExpect(status().isCreated())).get("id").asText();
        String raw=tokens.issue();jdbc.sql("UPDATE client_invitations SET token_hash=? WHERE organization_id=? AND id=?").params(tokens.hash(raw),org,UUID.fromString(invitation)).update();
        postJson("/client-invitations/accept",null,Map.of("token",raw,"password","ClientForm7Password")).andExpect(status().isOk());
        var login=postJson("/client-auth/login",null,Map.of("organizationSlug",slug,"email","client-"+org+"@example.test","password","ClientForm7Password")).andExpect(status().isOk()).andReturn();
        String cookie=login.getResponse().getHeader("Set-Cookie");client=new Cookie("BOS_SESSION",cookie.substring("BOS_SESSION=".length(),cookie.indexOf(';')));
        portalPath="/client-portal/projects/"+project+"/payments/"+step;
    }

    @Autowired FakeProvider provider;
    String invoice() throws Exception {
        String id=data(mvc.perform(post("/api/v1/invoices").cookie(admin).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(json.writeValueAsString(Map.of("stepId",step,"dueDate",java.time.LocalDate.now().plusDays(7).toString(),"items",List.of(Map.of("description","Launch","quantity",1,"unitAmountMinor",10000,"taxBasisPoints",0)))))).andExpect(status().isCreated())).at("/invoice/id").asText();
        postJson("/invoices/"+id+"/send",admin,Map.of("version",0)).andExpect(status().isOk());return id;
    }
    JsonNode checkout(String invoice,long amount,String key) throws Exception {return data(checkoutResult(invoice,amount,key).andExpect(status().isOk()));}
    ResultActions checkoutResult(String invoice,long amount,String key)throws Exception{return mvc.perform(post("/api/v1/client-portal/invoices/"+invoice+"/checkout").cookie(client).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(Map.of("amountMinor",amount))));}
    void capture(JsonNode checkout,String paymentId){provider.payments.put(paymentId,new com.brainserve.clientonboarding.payments.application.PaymentProvider.Payment(paymentId,checkout.get("orderId").asText(),checkout.get("amountMinor").asLong(),0,"INR","captured",true));}
    ResultActions webhook(String event,String type,String paymentId,boolean valid)throws Exception {
        byte[] body=json.writeValueAsBytes(Map.of("event",type,"payload",Map.of(type.startsWith("refund.")?"refund":"payment",Map.of("entity",Map.of("id",paymentId)))));
        return mvc.perform(post("/api/v1/webhooks/payments/razorpay/"+org).header("X-Razorpay-Event-Id",event).header("X-Razorpay-Signature",valid?FakeProvider.sign(body):"00".repeat(32)).contentType("application/json").content(body));
    }
    @Test void partialPaymentsDuplicateWebhooksRefundAndRecollectionPreserveReadiness()throws Exception {
        String invoice=invoice();var first=checkout(invoice,3000,"first-attempt");
        checkout(invoice,3000,"first-attempt");assertThat(provider.creates).isEqualTo(1);
        checkoutResult(invoice,4000,"another-attempt").andExpect(status().isConflict());
        capture(first,"pay_first");webhook("evt_first","payment.captured","pay_first",false).andExpect(status().isUnauthorized());
        getJson("/invoices/"+invoice,admin).andExpect(jsonPath("$.data.paidMinor").value(0));
        webhook("evt_first","payment.captured","pay_first",true).andExpect(status().isOk());
        webhook("evt_first","payment.captured","pay_first",true).andExpect(status().isOk());
        webhook("evt_same_fact","payment.captured","pay_first",true).andExpect(status().isOk());
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        var second=checkout(invoice,7000,"second-attempt");capture(second,"pay_second");webhook("evt_second","payment.captured","pay_second",true).andExpect(status().isOk());
        getJson("/invoices/"+invoice,admin).andExpect(jsonPath("$.data.paidMinor").value(10000)).andExpect(jsonPath("$.data.invoice.status").value("PAID"));
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(true));
        String transaction=jdbc.sql("SELECT id FROM payment_transactions WHERE organization_id=? AND provider_payment_id='pay_second'").param(org).query(UUID.class).single().toString();
        var refund=data(mvc.perform(post("/api/v1/payment-transactions/"+transaction+"/refunds").cookie(admin).with(csrf()).header("Idempotency-Key","refund-second").contentType("application/json").content(json.writeValueAsString(Map.of("amountMinor",7000,"reason","Scope reduction")))).andExpect(status().isOk()));
        assertThat(refund.get("status").asText()).isEqualTo("PROCESSED");
        getJson("/invoices/"+invoice,admin).andExpect(jsonPath("$.data.paidMinor").value(3000));
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        var third=checkout(invoice,7000,"third-attempt");capture(third,"pay_third");webhook("evt_third","payment.captured","pay_third",true).andExpect(status().isOk());
        getJson("/invoices/"+invoice,admin).andExpect(jsonPath("$.data.paidMinor").value(10000));
        assertThat(jdbc.sql("SELECT count(*) FROM billing_outbox_events WHERE organization_id=? AND event_type='PAYMENT_EVIDENCE_RECORDED'").param(org).query(Long.class).single()).isEqualTo(3);
        assertThatThrownBy(()->jdbc.sql("UPDATE invoices SET total_minor=1 WHERE organization_id=?").param(org).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("DELETE FROM payment_webhook_events WHERE organization_id=?").param(org).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void authorizationTenantIsolationCsrfAndGenericStepBypassAreRejected()throws Exception {
        String id=invoice();getJson("/invoices/"+id,null).andExpect(status().isUnauthorized());getJson("/invoices/"+id,foreign).andExpect(status().isNotFound());getJson("/invoices/"+id,client).andExpect(status().isForbidden());getJson("/invoices/"+id,reader).andExpect(status().isOk());
        postJson("/invoices/"+id+"/send",reader,Map.of("version",1)).andExpect(status().isForbidden());postJson("/invoices/"+id+"/close",foreign,Map.of("version",1,"status","VOID","reason","Wrong tenant")).andExpect(status().isNotFound());
        getJson("/client-portal/projects/"+UUID.randomUUID()+"/payments/"+step,client).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/client-portal/invoices/"+id+"/checkout").cookie(client).header("Idempotency-Key","without-csrf").contentType("application/json").content("{\"amountMinor\":5000}")).andExpect(status().isForbidden());
        getJson("/invoices?page=-1",admin).andExpect(status().isBadRequest());
        checkoutResult(id,10001,"too-much-amount").andExpect(status().isBadRequest());
        String random=UUID.randomUUID().toString();checkoutResult(random,100,"foreign-invoice").andExpect(status().isNotFound());
        postJson("/client-portal/payments/"+checkout(id,5000,"valid-attempt").get("id").asText()+"/confirm",client,Map.of("paymentId","pay_fake","signature","00".repeat(32))).andExpect(status().isBadRequest());
    }
    @Test void uncertainOrderRetainsReservationAndCanRecoverByProviderReceipt()throws Exception {
        String id=invoice();provider.timeout=true;checkoutResult(id,5000,"unknown-order").andExpect(status().isBadGateway());
        getJson("/invoices/"+id,admin).andExpect(jsonPath("$.data.invoice.reservedMinor").value(5000));
        var pending=checkout(id,5000,"unknown-order");assertThat(pending.get("status").asText()).isEqualTo("UNKNOWN");assertThat(provider.creates).isEqualTo(1);
        var order=provider.orders.values().iterator().next();provider.timeout=false;
        postJson("/payments/"+pending.get("id").asText()+"/reconcile",admin,Map.of("providerId",order.id())).andExpect(status().isOk());
        var recovered=checkout(id,5000,"unknown-order");assertThat(recovered.get("orderId").asText()).isEqualTo(order.id());assertThat(provider.creates).isEqualTo(1);
        capture(recovered,"pay_recovered");webhook("evt_recovered","payment.captured","pay_recovered",true).andExpect(status().isOk());
    }
    @Test void webhookRecordsMoneyOnHoldAndSyncCompletesOnlyAfterResume()throws Exception {
        String id=invoice();var checkout=checkout(id,5000,"hold-attempt");capture(checkout,"pay_held");
        jdbc.sql("UPDATE projects SET status='ON_HOLD' WHERE organization_id=? AND id=?").params(org,UUID.fromString(project)).update();
        webhook("evt_held","payment.captured","pay_held",true).andExpect(status().isOk());getJson("/invoices/"+id,admin).andExpect(jsonPath("$.data.paidMinor").value(5000));
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        jdbc.sql("UPDATE projects SET status='ONBOARDING' WHERE organization_id=? AND id=?").params(org,UUID.fromString(project)).update();postJson("/invoices/"+id+"/sync-workflow",admin,Map.of()).andExpect(status().isOk());
        getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(true));
    }
    @Test void concurrentCheckoutCreatesExactlyOneProviderOrder()throws Exception {
        String id=invoice();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {var a=pool.submit(()->checkoutResult(id,5000,"concurrent-first").andReturn().getResponse().getStatus());var b=pool.submit(()->checkoutResult(id,5000,"concurrent-second").andReturn().getResponse().getStatus());assertThat(a.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);assertThat(b.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);assertThat(provider.creates).isEqualTo(1);}
        finally{pool.shutdownNow();}
    }
    @Test void manualOverrideRefundAndIdempotencyArePermissionBounded()throws Exception {
        String id=invoice();var body=Map.of("amountMinor",5000,"reference","bank-ref-"+org,"reason","Verified transfer");
        java.util.function.Function<Cookie,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> request=c->post("/api/v1/invoices/"+id+"/manual-payments").cookie(c).with(csrf()).header("Idempotency-Key","manual-command").contentType("application/json");
        String payload=json.writeValueAsString(body);mvc.perform(request.apply(reader).content(payload)).andExpect(status().isForbidden());mvc.perform(request.apply(foreign).content(payload)).andExpect(status().isNotFound());
        var t=data(mvc.perform(request.apply(admin).content(payload)).andExpect(status().isOk()));mvc.perform(request.apply(admin).content(payload)).andExpect(status().isOk());
        mvc.perform(request.apply(admin).content(json.writeValueAsString(Map.of("amountMinor",4000,"reference","different-ref","reason","Changed")))).andExpect(status().isConflict());
        getJson("/invoices",admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));getJson("/invoices?status=PARTIALLY_PAID&projectId="+project,admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        String refundPath="/api/v1/payment-transactions/"+t.get("id").asText()+"/refunds";
        mvc.perform(post(refundPath).cookie(reader).with(csrf()).header("Idempotency-Key","refund-command").contentType("application/json").content("{\"amountMinor\":100,\"reason\":\"Refund\"}")).andExpect(status().isForbidden());
        String refundBody="{\"amountMinor\":1000,\"reason\":\"Scope reduction\"}";
        for(int n=0;n<2;n++)mvc.perform(post(refundPath).cookie(admin).with(csrf()).header("Idempotency-Key","refund-command").contentType("application/json").content(refundBody)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROCESSED"));
        getJson("/invoices/"+id,admin).andExpect(jsonPath("$.data.paidMinor").value(4000));getJson("/onboardings/"+onboarding,admin).andExpect(jsonPath("$.data.onboarding.ready").value(false));
        getJson("/payment-transactions/"+t.get("id").asText()+"/refunds",foreign).andExpect(status().isNotFound());
    }
    @Test void duplicateConcurrentEventsAndStaleFailureCannotDoubleCreditOrRegressCapture()throws Exception {
        String id=invoice();var checkout=checkout(id,5000,"race-capture");capture(checkout,"pay_race");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {var a=pool.submit(()->webhook("evt_race","payment.captured","pay_race",true).andReturn().getResponse().getStatus());var b=pool.submit(()->webhook("evt_race","payment.captured","pay_race",true).andReturn().getResponse().getStatus());assertThat(a.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);assertThat(b.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);}
        finally{pool.shutdownNow();}
        provider.payments.put("pay_race",new com.brainserve.clientonboarding.payments.application.PaymentProvider.Payment("pay_race",checkout.get("orderId").asText(),5000,0,"INR","failed",false));
        webhook("evt_late_failure","payment.failed","pay_race",true).andExpect(status().isOk());getJson("/invoices/"+id,admin).andExpect(jsonPath("$.data.paidMinor").value(5000));
        webhook("evt_race","payment.failed","pay_race",true).andExpect(status().isConflict());
        assertThat(jdbc.sql("SELECT count(*) FROM payment_transactions WHERE organization_id=?").param(org).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM billing_outbox_events WHERE organization_id=? AND event_type='PAYMENT_EVIDENCE_RECORDED'").param(org).query(Long.class).single()).isEqualTo(1);
    }
    @Test void invoiceCloseRetainsReasonAndPreventsStaleOrPaidMutation()throws Exception {
        String id=invoice();postJson("/invoices/"+id+"/close",admin,Map.of("version",0,"status","VOID","reason","Changed scope")).andExpect(status().isConflict());
        postJson("/invoices/"+id+"/close",admin,Map.of("version",1,"status","VOID","reason","Changed scope")).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT closed_reason FROM invoices WHERE organization_id=? AND id=?").params(org,UUID.fromString(id)).query(String.class).single()).isEqualTo("Changed scope");
        checkoutResult(id,5000,"closed-attempt").andExpect(status().isConflict());
        String replacement=invoice();assertThat(replacement).isNotEqualTo(id);var c=checkout(replacement,5000,"replacement-attempt");capture(c,"pay_replacement");webhook("evt_replacement","payment.captured","pay_replacement",true).andExpect(status().isOk());
        long version=data(getJson("/invoices/"+replacement,admin)).at("/invoice/version").asLong();postJson("/invoices/"+replacement+"/close",admin,Map.of("version",version,"status","CANCELLED","reason","Cannot discard paid history")).andExpect(status().isConflict());
    }
    @org.springframework.boot.test.context.TestConfiguration
    static class ProviderConfiguration {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary FakeProvider fakeProvider(){return new FakeProvider();}
    }
    static class FakeProvider implements com.brainserve.clientonboarding.payments.application.PaymentProvider {
        final java.util.concurrent.ConcurrentMap<String,Order> orders=new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentMap<String,Payment> payments=new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentMap<String,Refund> refunds=new java.util.concurrent.ConcurrentHashMap<>();
        volatile int creates;volatile boolean timeout;
        void reset(){orders.clear();payments.clear();refunds.clear();creates=0;timeout=false;}
        public boolean available(UUID org){return true;}public String publicKey(UUID org){return "rzp_test_fixture";}
        public synchronized Order createOrder(UUID org,UUID receipt,long amount){creates++;var o=new Order("order_"+creates,receipt.toString(),amount,"INR","created");orders.put(o.id(),o);if(timeout)throw new com.brainserve.clientonboarding.payments.application.ProviderFailure(false);return o;}
        public Order order(UUID org,String id){return orders.get(id);}public List<Payment> orderPayments(UUID org,String id){return payments.values().stream().filter(p->p.orderId().equals(id)).toList();}public Payment payment(UUID org,String id){return payments.get(id);}
        public Refund createRefund(UUID org,String paymentId,UUID receipt,long amount){var r=new Refund("rfnd_"+receipt.toString().replace("-",""),paymentId,receipt.toString(),amount,"INR","processed");refunds.put(r.id(),r);var p=payments.get(paymentId);payments.put(paymentId,new Payment(p.id(),p.orderId(),p.amountMinor(),p.refundedMinor()+amount,p.currency(),"refunded",true));return r;}
        public Refund refund(UUID org,String id){return refunds.get(id);}
        public boolean verifyWebhook(UUID org,byte[] body,String signature){return java.security.MessageDigest.isEqual(sign(body).getBytes(java.nio.charset.StandardCharsets.US_ASCII),signature.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
        public boolean verifyCheckout(UUID org,String order,String payment,String signature){return sign((order+"|"+payment).getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(signature);}
        static String sign(byte[] body){try{var m=javax.crypto.Mac.getInstance("HmacSHA256");m.init(new javax.crypto.spec.SecretKeySpec("test-webhook-secret-not-production".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));return java.util.HexFormat.of().formatHex(m.doFinal(body));}catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}}
    }
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
