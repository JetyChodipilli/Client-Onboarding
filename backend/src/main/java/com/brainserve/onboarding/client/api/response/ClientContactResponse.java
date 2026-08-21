package com.brainserve.onboarding.client.api.response;

import java.time.Instant;
import java.util.UUID;

public record ClientContactResponse(
        UUID id,
        UUID clientId,
        String displayName,
        String email,
        String jobTitle,
        String phone,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
