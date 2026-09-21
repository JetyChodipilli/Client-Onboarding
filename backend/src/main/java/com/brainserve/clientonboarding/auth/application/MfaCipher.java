package com.brainserve.clientonboarding.auth.application;

import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MfaCipher {
    private static final int IV_BYTES = 12;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public MfaCipher(AuthProperties properties) {
        this.properties = properties;
        key();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MFA secret encryption failed", exception);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_BYTES) throw new GeneralSecurityException("Invalid encrypted value");
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("MFA secret decryption failed", exception);
        }
    }

    private SecretKeySpec key() {
        try {
            if (properties.mfaEncryptionKey() == null || properties.mfaEncryptionKey().isBlank()) {
                throw new IllegalStateException("MFA_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
            }
            byte[] bytes = Base64.getDecoder().decode(properties.mfaEncryptionKey());
            if (bytes.length != 32) {
                throw new IllegalStateException("MFA_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must be valid base64", exception);
        }
    }
}
