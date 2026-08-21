package com.brainserve.onboarding.access.api.response;
import java.util.UUID;
public record PublishedPlatformAccessGuideResponse(UUID accessTypeId,String code,String name,UUID versionId,int versionNumber) {}
