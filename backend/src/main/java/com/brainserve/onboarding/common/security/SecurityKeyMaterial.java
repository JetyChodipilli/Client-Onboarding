package com.brainserve.onboarding.common.security;

import com.brainserve.onboarding.common.util.CryptoSupport;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityKeyMaterial {

    private static final Logger log = LoggerFactory.getLogger(SecurityKeyMaterial.class);

    private final SecretKey jwtKey;
    private final byte[] mfaEncryptionKey;

    public SecurityKeyMaterial(SecurityProperties properties) {
        this.jwtKey = new SecretKeySpec(resolveKey(
                properties.jwtSecretBase64(), 64, properties.requireExplicitSecrets(), "JWT"), "HmacSHA512");
        this.mfaEncryptionKey = resolveKey(
                properties.mfaEncryptionKeyBase64(), 32, properties.requireExplicitSecrets(), "MFA encryption");
    }

    public SecretKey jwtKey() {
        return jwtKey;
    }

    public byte[] mfaEncryptionKey() {
        return mfaEncryptionKey.clone();
    }

    private static byte[] resolveKey(String configured, int requiredBytes, boolean requireExplicit, String name) {
        if (configured != null && !configured.isBlank()) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(configured.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(name + " key must be valid Base64", ex);
            }
            if (decoded.length < requiredBytes) {
                throw new IllegalStateException(name + " key must contain at least " + requiredBytes + " bytes");
            }
            return decoded;
        }
        if (requireExplicit) {
            throw new IllegalStateException(name + " key is required when explicit secrets are enforced");
        }
        log.warn("{} key is not configured; generating ephemeral in-memory key for this process", name);
        return CryptoSupport.randomBytes(requiredBytes);
    }
}
