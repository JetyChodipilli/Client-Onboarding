package com.brainserve.onboarding.onboarding.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PreviewClientInvitationRequest(@NotBlank @Size(max=256) String token) {}
