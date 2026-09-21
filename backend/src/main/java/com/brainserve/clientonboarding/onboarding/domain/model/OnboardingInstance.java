package com.brainserve.clientonboarding.onboarding.domain.model;

import java.time.Instant;
import java.util.UUID;

public record OnboardingInstance(UUID id, UUID organizationId, UUID projectId, UUID sourceTemplateId,
                                 UUID sourceTemplateVersionId, int snapshotVersionNumber, String snapshotJson,
                                 Status status, boolean ready, Instant startedAt, Instant completedAt,
                                 Instant createdAt, Instant updatedAt, long version) {
    public enum Status { DRAFT, INVITED, IN_PROGRESS, AWAITING_INTERNAL_REVIEW, NEEDS_REVISION,
        APPROVED, COMPLETED, PAUSED, EXPIRED, CANCELLED }
}
