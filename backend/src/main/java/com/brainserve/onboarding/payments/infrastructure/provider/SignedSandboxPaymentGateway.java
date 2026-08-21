package com.brainserve.onboarding.payments.infrastructure.provider;

import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signed local/sandbox provider used for development and deterministic integration testing.
 * It is deliberately provider-specific and disabled unless app.payments.provider=SIGNED_SANDBOX.
 * Production deployments should supply a real provider adapter behind PaymentGateway.
 */
@Component
public class SignedSandboxPaymentGateway implements PaymentGateway {
    private static final String TIMESTAMP = "x-payment-timestamp";
    private static final String SIGNATURE = "x-payment-signature";
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public SignedSandboxPaymentGateway(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override public String providerCode() { return "SIGNED_SANDBOX"; }
    @Override public boolean available() { return providerCode().equals(properties.provider()) && secret().length >= 32; }

    @Override
    public CheckoutSession createCheckout(CheckoutCommand command) {
        String stable = command.organizationId() + ":" + command.invoiceId() + ":" + command.idempotencyKey();
        UUID deterministic = UUID.nameUUIDFromBytes(stable.getBytes(StandardCharsets.UTF_8));
        String paymentId = "pay_" + deterministic.toString().replace("-", "");
        String sessionId = "sess_" + UUID.nameUUIDFromBytes(("session:" + stable).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        String separator = properties.checkoutBaseUrl().contains("?") ? "&" : "?";
        String url = properties.checkoutBaseUrl() + separator
                + "paymentId=" + enc(command.paymentId().toString())
                + "&invoiceId=" + enc(command.invoiceId().toString())
                + "&projectId=" + enc(command.projectId().toString())
                + "&session=" + enc(sessionId);
        return new CheckoutSession(paymentId, sessionId, url, command.expiresAt());
    }

    @Override
    public RefundRequest requestRefund(RefundCommand command) {
        String stable = command.organizationId() + ":" + command.paymentId() + ":" + command.idempotencyKey();
        return new RefundRequest("rfnd_" + UUID.nameUUIDFromBytes(stable.getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""));
    }

    @Override
    public VerifiedWebhook verifyWebhook(byte[] body, Map<String, String> headers, Instant now) {
        if (!available()) throw new IllegalStateException("Signed sandbox payment provider is not configured");
        String timestampValue = header(headers, TIMESTAMP);
        String signature = header(headers, SIGNATURE);
        long seconds;
        try { seconds = Long.parseLong(timestampValue); }
        catch (RuntimeException ex) { throw new SecurityException("Payment webhook timestamp is invalid", ex); }
        Instant timestamp = Instant.ofEpochSecond(seconds);
        if (timestamp.isBefore(now.minus(properties.webhookMaxAge())) || timestamp.isAfter(now.plusSeconds(60))) {
            throw new SecurityException("Payment webhook timestamp is outside the accepted replay window");
        }
        byte[] expected = hmac(timestampValue + "." + new String(body, StandardCharsets.UTF_8));
        byte[] supplied;
        try { supplied = HexFormat.of().parseHex(signature.trim().toLowerCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new SecurityException("Payment webhook signature is invalid", ex); }
        if (!CryptoSupport.constantTimeEquals(expected, supplied)) throw new SecurityException("Payment webhook signature is invalid");

        try {
            JsonNode root = objectMapper.readTree(body);
            return new VerifiedWebhook(
                    requiredText(root, "eventId", 180), requiredText(root, "eventType", 80),
                    requiredUuid(root, "organizationId"), requiredUuid(root, "invoiceId"), requiredUuid(root, "paymentId"),
                    optionalText(root, "providerPaymentId", 180), optionalText(root, "providerTransactionId", 180),
                    optionalText(root, "providerRefundId", 180), requiredLong(root, "amountMinor"),
                    requiredText(root, "currency", 3).toUpperCase(Locale.ROOT), requiredInstant(root, "occurredAt"),
                    optionalText(root, "reason", 2000));
        } catch (SecurityException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Payment webhook payload is invalid", ex); }
    }

    public SignedWebhook signedEvent(String eventType, UUID organizationId, UUID invoiceId, UUID paymentId,
                                     String providerPaymentId, String providerRefundId, long amountMinor,
                                     String currency, String reason, Instant now) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", "evt_" + UUID.randomUUID().toString().replace("-", ""));
            payload.put("eventType", eventType);
            payload.put("organizationId", organizationId);
            payload.put("invoiceId", invoiceId);
            payload.put("paymentId", paymentId);
            payload.put("providerPaymentId", providerPaymentId);
            payload.put("providerTransactionId", "txn_" + UUID.randomUUID().toString().replace("-", ""));
            if (providerRefundId != null) payload.put("providerRefundId", providerRefundId);
            payload.put("amountMinor", amountMinor);
            payload.put("currency", currency);
            payload.put("occurredAt", now.toString());
            if (reason != null) payload.put("reason", reason);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            String ts = Long.toString(now.getEpochSecond());
            String sig = HexFormat.of().formatHex(hmac(ts + "." + new String(body, StandardCharsets.UTF_8)));
            return new SignedWebhook(body, Map.of(TIMESTAMP, ts, SIGNATURE, sig));
        } catch (Exception ex) { throw new IllegalStateException("Sandbox webhook could not be generated", ex); }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) { throw new IllegalStateException("HMAC-SHA256 is unavailable", ex); }
    }
    private byte[] secret() {
        String configured = properties.webhookSecretBase64();
        if (configured == null || configured.isBlank()) return new byte[0];
        try { return Base64.getDecoder().decode(configured.trim()); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Payment webhook secret must be Base64", ex); }
    }
    private static String header(Map<String,String> headers,String key){
        return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue)
                .filter(v->v!=null&&!v.isBlank()).findFirst().orElseThrow(()->new SecurityException("Required payment webhook signature header is missing"));
    }
    private static UUID requiredUuid(JsonNode root,String name){try{return UUID.fromString(requiredText(root,name,64));}catch(RuntimeException ex){throw new IllegalArgumentException(name+" is invalid",ex);}}
    private static long requiredLong(JsonNode root,String name){JsonNode n=root.get(name);if(n==null||!n.canConvertToLong()||n.longValue()<0)throw new IllegalArgumentException(name+" is invalid");return n.longValue();}
    private static Instant requiredInstant(JsonNode root,String name){try{return Instant.parse(requiredText(root,name,80));}catch(RuntimeException ex){throw new IllegalArgumentException(name+" is invalid",ex);}}
    private static String requiredText(JsonNode root,String name,int max){String v=optionalText(root,name,max);if(v==null)throw new IllegalArgumentException(name+" is required");return v;}
    private static String optionalText(JsonNode root,String name,int max){JsonNode n=root.get(name);if(n==null||n.isNull())return null;if(!n.isTextual())throw new IllegalArgumentException(name+" must be text");String v=n.asText().trim();if(v.isEmpty())return null;if(v.length()>max)throw new IllegalArgumentException(name+" is too long");return v;}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public record SignedWebhook(byte[] body,Map<String,String> headers){}
}
