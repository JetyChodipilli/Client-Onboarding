package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal lifecycle boundary for invitation/review modules implemented in later phases. */
@Service
public class OnboardingLifecycleService {
    private final OnboardingInstanceRepository onboardings;
    private final Clock clock;

    public OnboardingLifecycleService(OnboardingInstanceRepository onboardings, Clock clock) {
        this.onboardings = onboardings;
        this.clock = clock;
    }

    @Transactional
    public LifecycleResult transition(UUID organizationId, UUID onboardingId, OnboardingStatus target, UUID actorId) {
        OnboardingInstance onboarding = onboardings.findForUpdate(organizationId, onboardingId)
                .orElseThrow(OnboardingLifecycleService::notFound);
        OnboardingStatus before = onboarding.getStatus();
        try {
            onboarding.transitionTo(target, actorId, clock.instant());
            if (target == OnboardingStatus.IN_PROGRESS) {
                onboarding.updateReadiness(onboarding.isReady(), actorId, clock.instant());
            }
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STATE_INVALID", ex.getMessage());
        }
        onboardings.saveAndFlush(onboarding);
        return new LifecycleResult(before, onboarding.getStatus(), onboarding.isReady(), onboarding.getVersion());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested onboarding instance was not found.");
    }

    public record LifecycleResult(OnboardingStatus before, OnboardingStatus after, boolean ready, long version) {}
}
