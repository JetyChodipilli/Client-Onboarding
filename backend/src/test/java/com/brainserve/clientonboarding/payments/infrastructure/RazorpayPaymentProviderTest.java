package com.brainserve.clientonboarding.payments.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.brainserve.clientonboarding.payments.application.ProviderFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RazorpayPaymentProviderTest {
    final UUID org=UUID.randomUUID();
    RazorpayProperties config(){var p=new RazorpayProperties();p.setEnabled(true);var a=new RazorpayProperties.Account();a.setOrganizationId(org);a.setKeyId("rzp_test_contract");a.setKeySecret("contract-secret-only-for-testing");a.setWebhookSecret("current-webhook-secret-only-for-testing");a.setPreviousWebhookSecret("previous-webhook-secret-only-for-testing");p.setAccounts(List.of(a));return p;}
    @Test void signsExactBytesAcceptsRotationAndRejectsCrossTenantOrTampering(){
        var p=new RazorpayPaymentProvider(config(),new ObjectMapper());byte[] body="{\"amount\": 100}".getBytes(StandardCharsets.UTF_8);String signature=sign(config().getAccounts().get(0).getWebhookSecret(),body);
        assertThat(p.verifyWebhook(org,body,signature)).isTrue();assertThat(p.verifyWebhook(UUID.randomUUID(),body,signature)).isFalse();assertThat(p.verifyWebhook(org,"{}".getBytes(StandardCharsets.UTF_8),signature)).isFalse();assertThat(p.verifyWebhook(org,body,null)).isFalse();assertThat(p.verifyWebhook(org,body,sign(config().getAccounts().get(0).getPreviousWebhookSecret(),body))).isTrue();
        assertThat(p.verifyCheckout(org,"order_test","pay_test",sign(config().getAccounts().get(0).getKeySecret(),"order_test|pay_test".getBytes(StandardCharsets.UTF_8)))).isTrue();
    }
    @Test void realHttpAdapterUsesBasicAuthMinorUnitsImmutableReceiptAndNoRedirects()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var path=new AtomicReference<String>();var request=new AtomicReference<String>();var authorization=new AtomicReference<String>();
        UUID receipt=UUID.randomUUID();server.createContext("/v1/orders",exchange->{path.set(exchange.getRequestURI().getPath());request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));byte[] response=("{\"id\":\"order_contract\",\"receipt\":\""+receipt+"\",\"amount\":12345,\"currency\":\"INR\",\"status\":\"created\"}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);try(var out=exchange.getResponseBody()){out.write(response);}});server.start();
        try {var p=new RazorpayPaymentProvider(config(),new ObjectMapper(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/"));var result=p.createOrder(org,receipt,12345);assertThat(result.amountMinor()).isEqualTo(12345);assertThat(result.receipt()).isEqualTo(receipt.toString());assertThat(path.get()).isEqualTo("/v1/orders");var json=new ObjectMapper().readTree(request.get());assertThat(json.get("amount").asLong()).isEqualTo(12345);assertThat(json.get("partial_payment").asBoolean()).isFalse();assertThat(authorization.get()).isEqualTo("Basic "+Base64.getEncoder().encodeToString("rzp_test_contract:contract-secret-only-for-testing".getBytes(StandardCharsets.UTF_8)));assertThatThrownBy(()->p.payment(org,"pay_../../secrets")).isInstanceOf(com.brainserve.clientonboarding.common.error.DomainException.class);}
        finally{server.stop(0);}
    }
    @Test void providerServerErrorIsAmbiguousAndInvalidConfigurationFailsClosed()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/orders",e->{e.sendResponseHeaders(503,-1);e.close();});server.start();
        try {var p=new RazorpayPaymentProvider(config(),new ObjectMapper(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/"));assertThatThrownBy(()->p.createOrder(org,UUID.randomUUID(),100)).isInstanceOfSatisfying(ProviderFailure.class,e->assertThat(e.definite()).isFalse());}
        finally{server.stop(0);}
        var c=config();c.getAccounts().get(0).setWebhookSecret("weak");assertThatThrownBy(()->new RazorpayPaymentProvider(c,new ObjectMapper())).isInstanceOf(IllegalStateException.class);
    }
    static String sign(String key,byte[] body){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(body));}catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}}
}
