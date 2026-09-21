package com.brainserve.clientonboarding.onboarding.domain.repository;

import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingRepository {
    Optional<OnboardingInstance> findById(UUID organizationId, UUID onboardingId);
    Optional<OnboardingInstance> findByProject(UUID organizationId, UUID projectId);
    void lockTenantCommands(UUID organizationId);
    Optional<IdempotentCommand> findIdempotentCommand(UUID organizationId, String scope, String key);
    void insertIdempotency(UUID organizationId, String scope, String key, UUID resourceId,
                           String fingerprint, Instant now);
    OnboardingInstance insert(OnboardingInstance instance, List<OnboardingStepInstance> steps, UUID actorId);
    List<OnboardingStepInstance> findSteps(UUID organizationId, UUID onboardingId);
    Optional<OnboardingStepInstance> findStep(UUID organizationId, UUID stepId);
    boolean updateStepStatus(UUID organizationId, UUID stepId, OnboardingStepInstance.Status current,
                             OnboardingStepInstance.Status next, long version, UUID actorId, Instant now);
    void refreshAvailability(UUID organizationId, UUID onboardingId, UUID actorId, Instant now);
    boolean updateReadiness(UUID organizationId, UUID onboardingId, boolean ready, long version,
                            UUID actorId, Instant now);

    record IdempotentCommand(UUID resourceId, String requestFingerprint) { }
}
