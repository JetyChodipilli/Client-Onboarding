package com.brainserve.clientonboarding.organization.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Role(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        Set<String> permissions,
        Instant archivedAt,
        long version
) {
}
