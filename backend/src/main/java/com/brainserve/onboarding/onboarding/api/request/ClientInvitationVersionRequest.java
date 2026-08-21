package com.brainserve.onboarding.onboarding.api.request;
import jakarta.validation.constraints.PositiveOrZero;
public record ClientInvitationVersionRequest(@PositiveOrZero long version) {}
