package com.brainserve.onboarding.client.api.response;

import com.brainserve.onboarding.client.domain.model.ClientStatus;
import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        ClientStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
