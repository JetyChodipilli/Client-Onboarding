package com.brainserve.clientonboarding.workflow.domain.model;

import java.time.Instant;
import java.util.UUID;

public record TemplateVersion(UUID id, UUID organizationId, UUID templateId, int versionNumber, Status status,
                              Instant publishedAt, UUID publishedBy, Instant createdAt, Instant updatedAt,
                              long version) {
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
}
