package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.common.util.CryptoSupport;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int TIME_STEP_SECONDS = 30;
    private final Clock clock;

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    public String generateSecret() {
        return base32Encode(CryptoSupport.randomBytes(20));
    }

    public boolean verify(String base32Secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long counter = clock.instant().getEpochSecond() / TIME_STEP_SECONDS;
        int expected = Integer.parseInt(code);
        for (long drift = -1; drift <= 1; drift++) {
            if (generate(base32Secret, counter + drift) == expected) return true;
        }
        return false;
    }

    private int generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return binary % 1_000_000;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP algorithm is unavailable", ex);
        }
    }

    private static String base32Encode(byte[] input) {
        StringBuilder out = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) out.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
        return out.toString();
    }

    private static byte[] base32Decode(String input) {
        String value = input.replace("=", "").trim().toUpperCase(java.util.Locale.ROOT);
        byte[] output = new byte[value.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : value.toCharArray()) {
            int digit = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32 value");
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output;
    }
}
