package com.brainserve.onboarding.access.api.response;
import com.brainserve.onboarding.access.domain.model.PlatformAccessRequestStatus;import java.time.Instant;import java.util.UUID;
public record PlatformAccessQueueItemResponse(UUID id,UUID projectId,UUID onboardingId,UUID stepId,String accessTypeCode,String accessTypeName,PlatformAccessRequestStatus status,String accountIdentifier,Instant submittedAt,Instant updatedAt,long version) {}
