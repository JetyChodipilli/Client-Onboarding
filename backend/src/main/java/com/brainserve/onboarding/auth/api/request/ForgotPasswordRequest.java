package com.brainserve.onboarding.auth.api.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ForgotPasswordRequest(@Email @NotBlank @Size(max=320) String email, @NotBlank @Size(max=80) String organizationSlug) {}
