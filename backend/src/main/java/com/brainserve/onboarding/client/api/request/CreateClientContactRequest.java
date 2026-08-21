package com.brainserve.onboarding.client.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientContactRequest(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 120) String jobTitle,
        @Size(max = 40) String phone) {}
