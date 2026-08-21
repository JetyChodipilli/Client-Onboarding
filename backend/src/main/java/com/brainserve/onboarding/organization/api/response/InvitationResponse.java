package com.brainserve.onboarding.organization.api.response;
import java.time.Instant;
import java.util.UUID;
public record InvitationResponse(UUID id, String email, String displayName, String status, Instant expiresAt) {}
