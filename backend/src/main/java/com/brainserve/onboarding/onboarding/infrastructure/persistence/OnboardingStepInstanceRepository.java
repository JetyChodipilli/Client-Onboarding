package com.brainserve.onboarding.onboarding.infrastructure.persistence;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OnboardingStepInstanceRepository extends JpaRepository<OnboardingStepInstance, UUID> {
    Optional<OnboardingStepInstance> findByOrganizationIdAndId(UUID organizationId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OnboardingStepInstance s where s.organizationId=:organizationId and s.id=:stepId")
    Optional<OnboardingStepInstance> findForUpdate(UUID organizationId, UUID stepId);
    List<OnboardingStepInstance> findAllByOrganizationIdAndOnboardingIdOrderByDisplayOrderAscIdAsc(UUID organizationId, UUID onboardingId);
    List<OnboardingStepInstance> findAllByOrganizationIdAndOnboardingIdInOrderByOnboardingIdAscDisplayOrderAscIdAsc(
            UUID organizationId, Collection<UUID> onboardingIds);
}
