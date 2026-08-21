package com.brainserve.onboarding.organization.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record UpdateOrganizationRequest(@NotBlank @Size(max=180) String name, @PositiveOrZero long version) {}
