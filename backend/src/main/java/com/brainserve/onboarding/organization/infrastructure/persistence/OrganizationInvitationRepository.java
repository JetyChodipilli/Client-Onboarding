package com.brainserve.onboarding.organization.infrastructure.persistence;

import com.brainserve.onboarding.organization.domain.model.OrganizationInvitation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrganizationInvitation i where i.tokenHash = :tokenHash")
    Optional<OrganizationInvitation> findByTokenHashForUpdate(String tokenHash);

    Optional<OrganizationInvitation> findFirstByOrganizationIdAndNormalizedEmailAndStatusOrderByCreatedAtDesc(
            UUID organizationId, String normalizedEmail, String status);
}
