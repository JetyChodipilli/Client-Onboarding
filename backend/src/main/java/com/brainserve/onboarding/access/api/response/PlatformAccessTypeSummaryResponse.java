package com.brainserve.onboarding.access.api.response;
import com.brainserve.onboarding.access.domain.model.PlatformAccessTypeStatus;import java.time.Instant;import java.util.UUID;
public record PlatformAccessTypeSummaryResponse(UUID id,String code,String name,String description,PlatformAccessTypeStatus status,Integer publishedVersion,Instant updatedAt,long version) {}
