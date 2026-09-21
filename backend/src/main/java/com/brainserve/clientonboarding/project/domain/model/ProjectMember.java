package com.brainserve.clientonboarding.project.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectMember(UUID id, UUID organizationId, UUID projectId, UUID membershipId,
                            UUID userId, String displayName, String email, String assignmentRole,
                            Instant createdAt) { }
