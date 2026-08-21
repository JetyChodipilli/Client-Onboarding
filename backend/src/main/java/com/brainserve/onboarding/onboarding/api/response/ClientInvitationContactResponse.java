package com.brainserve.onboarding.onboarding.api.response;
import java.util.UUID;
public record ClientInvitationContactResponse(UUID id, String displayName, String email) {}
