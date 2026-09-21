package com.brainserve.clientonboarding.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Organization(
        UUID id,
        String slug,
        String name,
        OrganizationStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public enum OrganizationStatus { ACTIVE, SUSPENDED, ARCHIVED }
}
