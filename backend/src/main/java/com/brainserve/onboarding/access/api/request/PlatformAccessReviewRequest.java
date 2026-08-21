package com.brainserve.onboarding.access.api.request;
import jakarta.validation.constraints.Size;
public record PlatformAccessReviewRequest(@Size(max=2000) String reason,long version) {}
