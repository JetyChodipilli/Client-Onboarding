package com.brainserve.onboarding.onboarding.infrastructure.persistence;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstanceDependency;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingStepInstanceDependencyRepository extends JpaRepository<OnboardingStepInstanceDependency, OnboardingStepInstanceDependency.Key> {
    List<OnboardingStepInstanceDependency> findAllByOrganizationIdAndOnboardingId(UUID organizationId, UUID onboardingId);
    List<OnboardingStepInstanceDependency> findAllByOrganizationIdAndOnboardingIdIn(UUID organizationId, Collection<UUID> onboardingIds);
}
