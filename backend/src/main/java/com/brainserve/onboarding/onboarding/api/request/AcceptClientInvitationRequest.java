package com.brainserve.onboarding.onboarding.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptClientInvitationRequest(
        @NotBlank @Size(max=256) String token,
        @NotBlank @Size(min=1,max=128) String password) {}
