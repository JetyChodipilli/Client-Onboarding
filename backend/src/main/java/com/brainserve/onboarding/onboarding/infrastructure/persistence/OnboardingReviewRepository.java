package com.brainserve.onboarding.onboarding.infrastructure.persistence;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingReview;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingReviewAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingReviewRepository extends JpaRepository<OnboardingReview, UUID> {
    List<OnboardingReview> findAllByOrganizationIdAndOnboardingIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID onboardingId);
    Optional<OnboardingReview> findFirstByOrganizationIdAndOnboardingIdAndActionOrderByCreatedAtDescIdDesc(
            UUID organizationId, UUID onboardingId, OnboardingReviewAction action);
}
