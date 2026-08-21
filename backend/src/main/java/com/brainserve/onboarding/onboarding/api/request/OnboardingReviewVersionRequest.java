package com.brainserve.onboarding.onboarding.api.request;

import jakarta.validation.constraints.Min;

public record OnboardingReviewVersionRequest(@Min(0) long version) {}
