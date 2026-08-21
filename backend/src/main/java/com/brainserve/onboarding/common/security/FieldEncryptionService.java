package com.brainserve.onboarding.common.security;

import com.brainserve.onboarding.common.util.CryptoSupport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class FieldEncryptionService {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;

    public FieldEncryptionService(SecurityKeyMaterial keyMaterial) {
        this.key = new SecretKeySpec(keyMaterial.mfaEncryptionKey(), "AES");
    }

    public String encrypt(String plaintext, String context) {
        try {
            byte[] iv = CryptoSupport.randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(packed.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt sensitive field", ex);
        }
    }

    public String decrypt(String encoded, String context) {
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            if (packed.length <= IV_BYTES) {
                throw new IllegalArgumentException("Encrypted value is malformed");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            System.arraycopy(packed, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt sensitive field", ex);
        }
    }
}
