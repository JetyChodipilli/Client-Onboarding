package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingReviewAction;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Keeps final-review revision requests separate from mathematical readiness.
 *
 * <p>The PRD defines readiness from blocking requirements only. A reviewer can still return a non-blocking
 * requirement for revision, so the lifecycle must not re-enter final review until every explicitly selected
 * revision requirement is complete.</p>
 */
@Component
public class FinalReviewRevisionGate {
    private final OnboardingReviewRepository reviews;

    public FinalReviewRevisionGate(OnboardingReviewRepository reviews) {
        this.reviews = reviews;
    }

    public boolean hasOutstanding(UUID organizationId, UUID onboardingId, List<OnboardingStepInstance> steps) {
        var latest = reviews.findFirstByOrganizationIdAndOnboardingIdAndActionOrderByCreatedAtDescIdDesc(
                organizationId, onboardingId, OnboardingReviewAction.REVISION_REQUESTED).orElse(null);
        if (latest == null) return false;
        var revisionIds = latest.getRevisionStepIds();
        if (revisionIds == null || !revisionIds.isArray()) return true;

        Map<UUID, OnboardingStepStatus> statusById = steps.stream().collect(Collectors.toMap(
                OnboardingStepInstance::getId, OnboardingStepInstance::getStatus));
        for (var node : revisionIds) {
            try {
                UUID id = UUID.fromString(node.asText());
                if (statusById.get(id) != OnboardingStepStatus.COMPLETED) return true;
            } catch (IllegalArgumentException ex) {
                // Review history is append-only. Malformed evidence must fail closed rather than advance the lifecycle.
                return true;
            }
        }
        return false;
    }
}
