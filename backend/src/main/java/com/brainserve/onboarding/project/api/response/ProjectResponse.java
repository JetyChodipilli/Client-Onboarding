package com.brainserve.onboarding.project.api.response;

import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID clientId,
        String clientName,
        UUID serviceId,
        String serviceName,
        String name,
        String description,
        ProjectStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
