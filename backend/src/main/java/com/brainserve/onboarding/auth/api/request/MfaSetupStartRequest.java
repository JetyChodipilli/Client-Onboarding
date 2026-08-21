package com.brainserve.onboarding.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaSetupStartRequest(@NotBlank @Size(max = 256) String setupToken) {}
