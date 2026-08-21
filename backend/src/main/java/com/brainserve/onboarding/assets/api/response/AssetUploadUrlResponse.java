package com.brainserve.onboarding.assets.api.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssetUploadUrlResponse(
        UUID assetId,
        UUID assetVersionId,
        int versionNumber,
        String filename,
        String uploadUrl,
        String method,
        Map<String, List<String>> headers,
        Instant expiresAt) {
    public AssetUploadUrlResponse { headers = Map.copyOf(headers); }
}
