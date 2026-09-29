package com.brainserve.clientonboarding.payments.infrastructure;

import com.brainserve.clientonboarding.payments.application.*;
import com.brainserve.clientonboarding.billing.application.BillingErrors;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayPaymentProvider implements PaymentProvider {
    private final RazorpayProperties config;private final ObjectMapper json;private final HttpClient http;private final URI base;
    @Autowired
    public RazorpayPaymentProvider(RazorpayProperties config,ObjectMapper json){this(config,json,URI.create("https://api.razorpay.com/v1/"));}
    RazorpayPaymentProvider(RazorpayProperties config,ObjectMapper json,URI base){config.validate();this.config=config;this.json=json;this.base=base;http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();}
    public boolean available(UUID org){return config.account(org).isPresent();}
    public String publicKey(UUID org){return account(org).getKeyId();}
    public Order createOrder(UUID org,UUID receipt,long amount){return order(request(org,"POST","orders",Map.of("amount",amount,"currency","INR","receipt",receipt.toString(),"partial_payment",false)));}
    public Order order(UUID org,String id){return order(request(org,"GET","orders/"+id(id,"order"),null));}
    public List<Payment> orderPayments(UUID org,String id){var items=request(org,"GET","orders/"+id(id,"order")+"/payments",null).path("items");if(!items.isArray() || items.size()>100)throw new ProviderFailure(false);List<Payment> values=new ArrayList<>();items.forEach(p->values.add(payment(p)));return List.copyOf(values);}
    public Payment payment(UUID org,String id){return payment(request(org,"GET","payments/"+id(id,"pay"),null));}
    public Refund createRefund(UUID org,String paymentId,UUID receipt,long amount){return refund(request(org,"POST","payments/"+id(paymentId,"pay")+"/refund",Map.of("amount",amount,"speed","normal","receipt",receipt.toString())));}
    public Refund refund(UUID org,String id){return refund(request(org,"GET","refunds/"+id(id,"rfnd"),null));}
    public boolean verifyWebhook(UUID org,byte[] body,String signature){return config.account(org).map(a->matches(a.getWebhookSecret(),body,signature) || a.getPreviousWebhookSecret()!=null && matches(a.getPreviousWebhookSecret(),body,signature)).orElse(false);}
    public boolean verifyCheckout(UUID org,String order,String payment,String signature){return matches(account(org).getKeySecret(),(order+"|"+payment).getBytes(StandardCharsets.UTF_8),signature);}
    static boolean matches(String secret,byte[] body,String signature){
        if(signature==null || !signature.matches("[0-9a-fA-F]{64}"))return false;
        try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature));}
        catch(GeneralSecurityException e){throw new IllegalStateException("HMAC unavailable",e);}
    }
    private RazorpayProperties.Account account(UUID org){return config.account(org).orElseThrow(()->BillingErrors.state("Online payments are not configured for this organization. Contact your project team."));}
    private String id(String value,String prefix){if(value==null || !value.matches(prefix+"_[A-Za-z0-9]{1,80}"))throw BillingErrors.invalid("Invalid provider identifier.");return value;}
    private JsonNode request(UUID org,String method,String path,Object body){
        var a=account(org);
        try {
            var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("Authorization","Basic "+Base64.getEncoder().encodeToString((a.getKeyId()+":"+a.getKeySecret()).getBytes(StandardCharsets.UTF_8)));
            if(body==null)builder.GET();else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()){
                byte[] bytes=stream.readNBytes(1048577);
                if(response.statusCode()<200 || response.statusCode()>=300)throw new ProviderFailure(response.statusCode()>=400 && response.statusCode()<500 && response.statusCode()!=408 && response.statusCode()!=429);
                if(bytes.length>1048576)throw new ProviderFailure(false);
                return json.readTree(bytes);
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProviderFailure(false);}
        catch(IOException e){throw new ProviderFailure(false);}
    }
    private String text(JsonNode n,String key){var v=n.path(key);if(!v.isTextual() || v.asText().length()>160)throw new ProviderFailure(false);return v.asText();}
    private long amount(JsonNode n,String key){var v=n.path(key);if(!v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<0)throw new ProviderFailure(false);return v.longValue();}
    private Order order(JsonNode n){return new Order(text(n,"id"),text(n,"receipt"),amount(n,"amount"),text(n,"currency"),text(n,"status"));}
    private Payment payment(JsonNode n){return new Payment(text(n,"id"),text(n,"order_id"),amount(n,"amount"),amount(n,"amount_refunded"),text(n,"currency"),text(n,"status"),n.path("captured").asBoolean(false));}
    private Refund refund(JsonNode n){return new Refund(text(n,"id"),text(n,"payment_id"),n.path("receipt").isTextual()?n.path("receipt").asText():null,amount(n,"amount"),text(n,"currency"),text(n,"status"));}
}
