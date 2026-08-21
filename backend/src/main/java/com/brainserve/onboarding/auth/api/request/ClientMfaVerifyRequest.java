package com.brainserve.onboarding.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientMfaVerifyRequest(
        @NotBlank @Size(max = 256) String challengeToken,
        @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
