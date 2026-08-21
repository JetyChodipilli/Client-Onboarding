package com.brainserve.onboarding.servicecatalog.api.response;

import com.brainserve.onboarding.servicecatalog.domain.model.ServiceStatus;
import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String code,
        String name,
        String description,
        ServiceStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
