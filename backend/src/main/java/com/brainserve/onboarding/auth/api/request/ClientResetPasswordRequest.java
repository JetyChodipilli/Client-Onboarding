package com.brainserve.onboarding.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientResetPasswordRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(min = 12, max = 128) String newPassword) {}
