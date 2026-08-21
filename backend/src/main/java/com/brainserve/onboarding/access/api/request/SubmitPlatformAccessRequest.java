package com.brainserve.onboarding.access.api.request;
import jakarta.validation.constraints.Size;
public record SubmitPlatformAccessRequest(@Size(max=320) String accountIdentifier,@Size(max=4000) String note,long version) {}
