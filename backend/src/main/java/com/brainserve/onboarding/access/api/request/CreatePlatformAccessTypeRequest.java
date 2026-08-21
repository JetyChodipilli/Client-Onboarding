package com.brainserve.onboarding.access.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformAccessTypeRequest(
        @NotBlank @Size(max=80) String code,
        @NotBlank @Size(max=180) String name,
        @Size(max=2000) String description,
        @NotBlank @Size(max=20_000) String instructionsMarkdown,
        @Size(max=2000) String helpUrl,
        @Size(max=500) String changeNote) {}
