package com.brainserve.clientonboarding.common.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record RequestMetadata(String ipHash, String userAgentHash) {
    public static RequestMetadata from(HttpServletRequest request) {
        // Never trust a caller-supplied forwarding header here. A deployment that needs the
        // original address must normalize it at a trusted proxy/container boundary first.
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return new RequestMetadata(hash(ip == null ? "unknown" : ip),
                hash(userAgent == null ? "unknown" : userAgent));
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
