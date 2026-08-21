package com.brainserve.onboarding.access.api.response;
import com.brainserve.onboarding.access.domain.model.PlatformAccessTypeStatus;import java.time.Instant;import java.util.List;import java.util.UUID;
public record PlatformAccessTypeDetailResponse(UUID id,String code,String name,String description,PlatformAccessTypeStatus status,Instant updatedAt,long version,List<PlatformAccessGuideResponse> versions) { public PlatformAccessTypeDetailResponse { versions=List.copyOf(versions); } }
