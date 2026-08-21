package com.brainserve.onboarding.access.api.request;
import com.fasterxml.jackson.databind.JsonNode;import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.NotNull;import jakarta.validation.constraints.Size;
public record SavePlatformAccessGuideRequest(@NotBlank @Size(max=20_000) String instructionsMarkdown,@Size(max=2000) String helpUrl,@NotNull JsonNode resources,@Size(max=500) String changeNote,long version) {}
