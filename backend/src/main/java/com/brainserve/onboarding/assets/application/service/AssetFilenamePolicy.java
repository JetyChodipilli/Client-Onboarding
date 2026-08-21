package com.brainserve.onboarding.assets.application.service;

import java.text.Normalizer;

public final class AssetFilenamePolicy {
    private AssetFilenamePolicy() {}

    public static String sanitize(String original) {
        if (original == null || original.isBlank()) throw new IllegalArgumentException("Filename is required");
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^\\p{L}\\p{N}._() -]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isBlank() || name.equals(".") || name.equals("..")) name = "upload";
        if (name.length() > 180) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 20 ? name.substring(dot) : "";
            name = name.substring(0, Math.max(1, 180 - ext.length())).trim() + ext;
        }
        return name;
    }
}
