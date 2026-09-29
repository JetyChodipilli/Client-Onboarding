package com.brainserve.clientonboarding.assets.domain.model;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;

public final class AssetPolicy {
    public static final long MAX_BYTES = 50L * 1024 * 1024;
    public static final Set<String> MIME_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain", "video/mp4");
    private AssetPolicy() { }
    public static void requirement(List<String> mimes, long maxBytes) {
        if (mimes == null || mimes.isEmpty() || mimes.stream().anyMatch(java.util.Objects::isNull) || mimes.size() > MIME_TYPES.size() || !MIME_TYPES.containsAll(mimes)
                || Set.copyOf(mimes).size() != mimes.size() || maxBytes < 1 || maxBytes > MAX_BYTES)
            throw new IllegalArgumentException("Choose permitted file types and a size limit between 1 byte and 50 MiB.");
    }
    public static String filename(String value) {
        if (value == null || value.isBlank() || value.length() > 500) throw new IllegalArgumentException("Provide a filename of at most 500 characters.");
        String clean = Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[^a-zA-Z0-9._ -]", "_").replaceAll("\\.{2,}", "_").trim();
        clean = clean.replaceAll("^[. ]+", "");
        if (clean.isBlank()) clean = "asset";
        return clean.substring(0, Math.min(180, clean.length()));
    }
    public static void upload(AssetModels.Requirement r, String mime, long size, String sha256) {
        if (mime == null || !r.allowedMimes().contains(mime) || size < 1 || size > r.maxBytes()
                || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Check the permitted file type, size limit and SHA-256 checksum.");
    }
}
