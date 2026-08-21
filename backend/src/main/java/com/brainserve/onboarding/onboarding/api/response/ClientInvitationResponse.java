package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import com.brainserve.onboarding.onboarding.domain.model.ClientInvitationStatus;
import java.time.Instant;
import java.util.UUID;

public record ClientInvitationResponse(UUID id, UUID onboardingId, UUID projectId, UUID clientId, UUID contactId,
        String email, String displayName, ClientProjectAccessLevel accessLevel, ClientInvitationStatus status,
        Instant expiresAt, Instant lastSentAt, int resendCount, Instant acceptedAt, Instant revokedAt,
        Instant createdAt, long version) {}
