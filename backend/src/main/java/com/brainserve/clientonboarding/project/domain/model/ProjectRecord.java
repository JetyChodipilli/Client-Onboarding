package com.brainserve.clientonboarding.project.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectRecord(UUID id, UUID organizationId, UUID clientId, String clientName, String clientStatus,
                            UUID serviceId,
                            String serviceName, String serviceCode, String name, String description, Status status,
                            Long valueMinor, String currencyCode, LocalDate targetStartDate, Status previousStatus,
                            Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
    public enum Status { DRAFT, ONBOARDING, READY, ACTIVE, ON_HOLD, COMPLETED, CANCELLED, ARCHIVED }
}
