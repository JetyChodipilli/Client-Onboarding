package com.brainserve.onboarding.notifications.api.request;
import jakarta.validation.constraints.Min;public record ResourceVersionRequest(@Min(0) long version){}
