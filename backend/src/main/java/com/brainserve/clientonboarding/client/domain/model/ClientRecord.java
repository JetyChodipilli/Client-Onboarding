package com.brainserve.clientonboarding.client.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ClientRecord(UUID id, UUID organizationId, String name, String legalName, Status status,
                           String website, String email, String phone, String notes, Instant archivedAt,
                           Instant createdAt, Instant updatedAt, long version) {
    public enum Status { PROSPECT, ACTIVE, INACTIVE, ARCHIVED }
}
