package com.brainserve.onboarding.project.api.response;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID organizationUserId,
        String responsibility,
        Instant createdAt) {}
