package com.brainserve.onboarding.auth.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientLoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String organizationSlug,
        @NotBlank @Size(min = 1, max = 128) String password) {}
