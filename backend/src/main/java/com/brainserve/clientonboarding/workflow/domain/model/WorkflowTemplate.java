package com.brainserve.clientonboarding.workflow.domain.model;

import java.time.Instant;
import java.util.UUID;

public record WorkflowTemplate(UUID id, UUID organizationId, UUID serviceId, String name, String description,
                               Status status, Instant archivedAt, Instant createdAt, Instant updatedAt,
                               long version) {
    public enum Status { ACTIVE, ARCHIVED }
}
