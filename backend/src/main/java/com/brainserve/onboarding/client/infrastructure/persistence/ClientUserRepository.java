package com.brainserve.onboarding.client.infrastructure.persistence;

import com.brainserve.onboarding.client.domain.model.ClientUser;
import com.brainserve.onboarding.client.domain.model.ClientUserStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientUserRepository extends JpaRepository<ClientUser, UUID> {
    Optional<ClientUser> findByOrganizationIdAndClientIdAndUserId(UUID organizationId, UUID clientId, UUID userId);
    List<ClientUser> findAllByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, ClientUserStatus status);
    boolean existsByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, ClientUserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClientUser c where c.organizationId=:organizationId and c.clientId=:clientId and c.userId=:userId")
    Optional<ClientUser> findForUpdate(@Param("organizationId") UUID organizationId,
                                       @Param("clientId") UUID clientId,
                                       @Param("userId") UUID userId);
}
