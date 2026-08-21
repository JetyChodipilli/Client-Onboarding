package com.brainserve.onboarding.access.api.response;
import com.brainserve.onboarding.access.domain.model.PlatformAccessGuideVersionStatus;import com.fasterxml.jackson.databind.JsonNode;import java.time.Instant;import java.util.UUID;
public record PlatformAccessGuideResponse(UUID id,UUID accessTypeId,int versionNumber,PlatformAccessGuideVersionStatus status,String changeNote,String instructionsMarkdown,String helpUrl,JsonNode resources,Instant publishedAt,Instant updatedAt,long version) {}
