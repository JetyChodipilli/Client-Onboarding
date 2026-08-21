package com.brainserve.onboarding.access.api.request;
import jakarta.validation.constraints.Size;
public record CreatePlatformAccessGuideVersionRequest(@Size(max=500) String changeNote) {}
