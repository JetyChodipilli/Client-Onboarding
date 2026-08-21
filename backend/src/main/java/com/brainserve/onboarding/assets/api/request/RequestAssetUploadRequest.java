package com.brainserve.onboarding.assets.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequestAssetUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 160) String contentType,
        @Positive long sizeBytes,
        @Pattern(regexp = "(?i)^[0-9a-f]{64}$") String sha256) {}
