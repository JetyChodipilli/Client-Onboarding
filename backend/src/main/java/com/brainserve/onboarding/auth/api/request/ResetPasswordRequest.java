package com.brainserve.onboarding.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(max = 128) String newPassword) {}
