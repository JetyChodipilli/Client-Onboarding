package com.brainserve.onboarding.assets.api.response;

import java.time.Instant;

public record AssetDownloadUrlResponse(String url, Instant expiresAt, String filename, String contentType) {}
