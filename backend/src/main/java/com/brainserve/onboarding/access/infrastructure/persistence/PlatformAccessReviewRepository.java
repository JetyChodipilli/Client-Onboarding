package com.brainserve.onboarding.access.infrastructure.persistence;

import com.brainserve.onboarding.access.domain.model.PlatformAccessReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAccessReviewRepository extends JpaRepository<PlatformAccessReview, UUID> {
    List<PlatformAccessReview> findAllByOrganizationIdAndAccessRequestIdOrderByCreatedAtAscIdAsc(UUID organizationId, UUID requestId);
}
