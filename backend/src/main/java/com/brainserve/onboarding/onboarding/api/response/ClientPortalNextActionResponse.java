package com.brainserve.onboarding.onboarding.api.response;
import java.time.Instant;
import java.util.UUID;
public record ClientPortalNextActionResponse(UUID projectId, UUID stepId, String title, String description,
                                             Instant dueAt, String actionType) {}
