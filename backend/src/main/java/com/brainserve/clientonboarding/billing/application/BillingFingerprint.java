package com.brainserve.clientonboarding.billing.application;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class BillingFingerprint {
    private final ObjectMapper mapper;
    public BillingFingerprint(ObjectMapper mapper) {this.mapper=mapper;}
    public String of(Object value) {
        try { return bytes(mapper.writeValueAsBytes(value)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Cannot fingerprint billing request",e);}
    }
    public static String bytes(byte[] value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
