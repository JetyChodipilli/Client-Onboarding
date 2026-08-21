package com.brainserve.onboarding.onboarding.infrastructure.persistence;

import com.brainserve.onboarding.onboarding.domain.model.ClientInvitation;
import com.brainserve.onboarding.onboarding.domain.model.ClientInvitationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientInvitationRepository extends JpaRepository<ClientInvitation, UUID> {
    List<ClientInvitation> findAllByOrganizationIdAndOnboardingIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID onboardingId);
    Optional<ClientInvitation> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<ClientInvitation> findByTokenHash(String tokenHash);
    Optional<ClientInvitation> findByOrganizationIdAndOnboardingIdAndContactIdAndStatus(UUID organizationId, UUID onboardingId, UUID contactId, ClientInvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ClientInvitation i where i.organizationId=:organizationId and i.id=:id")
    Optional<ClientInvitation> findForUpdate(@Param("organizationId") UUID organizationId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ClientInvitation i where i.tokenHash=:hash")
    Optional<ClientInvitation> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update ClientInvitation i set i.status=:expired, i.updatedAt=:now where i.organizationId=:organizationId and i.onboardingId=:onboardingId and i.status=:pending and i.expiresAt<=:now")
    int expirePendingForOnboarding(UUID organizationId, UUID onboardingId, Instant now, ClientInvitationStatus pending, ClientInvitationStatus expired);

    @Modifying
    @Query("update ClientInvitation i set i.status=:expired, i.updatedAt=:now where i.organizationId=:organizationId and i.id=:id and i.status=:pending and i.expiresAt<=:now")
    int expirePendingById(UUID organizationId, UUID id, Instant now, ClientInvitationStatus pending, ClientInvitationStatus expired);
}
