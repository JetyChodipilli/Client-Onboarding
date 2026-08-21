package com.brainserve.onboarding.access.api.response;
import com.brainserve.onboarding.access.domain.model.PlatformAccessReviewAction;import java.time.Instant;import java.util.UUID;
public record PlatformAccessReviewResponse(UUID id,PlatformAccessReviewAction action,String reason,Instant createdAt,UUID createdBy) {}
