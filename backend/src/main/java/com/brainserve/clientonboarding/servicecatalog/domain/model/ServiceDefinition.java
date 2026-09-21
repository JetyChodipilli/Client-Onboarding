package com.brainserve.clientonboarding.servicecatalog.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ServiceDefinition(UUID id, UUID organizationId, String code, String name, String description,
                                Status status, Instant archivedAt, Instant createdAt, Instant updatedAt,
                                long version) {
    public enum Status { ACTIVE, INACTIVE, ARCHIVED }
}
