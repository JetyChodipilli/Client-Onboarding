package com.brainserve.clientonboarding.project.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityEntry(UUID id, UUID organizationId, UUID projectId, UUID actorUserId, String action,
                            String entityType, UUID entityId, String details, Instant createdAt) { }
