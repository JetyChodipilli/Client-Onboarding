package com.brainserve.onboarding.access.api.request;
import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Size;
public record UpdatePlatformAccessTypeRequest(@NotBlank @Size(max=180) String name,@Size(max=2000) String description,long version) {}
