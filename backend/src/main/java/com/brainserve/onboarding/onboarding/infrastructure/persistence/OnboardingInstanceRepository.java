package com.brainserve.onboarding.onboarding.infrastructure.persistence;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnboardingInstanceRepository extends JpaRepository<OnboardingInstance, UUID> {
    Optional<OnboardingInstance> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<OnboardingInstance> findByOrganizationIdAndProjectId(UUID organizationId, UUID projectId);
    List<OnboardingInstance> findAllByOrganizationIdAndProjectIdIn(UUID organizationId, Collection<UUID> projectIds);
    Optional<OnboardingInstance> findByOrganizationIdAndStartIdempotencyKeyHash(UUID organizationId, String hash);
    Page<OnboardingInstance> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OnboardingInstance o where o.organizationId = :organizationId and o.id = :id")
    Optional<OnboardingInstance> findForUpdate(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
}
