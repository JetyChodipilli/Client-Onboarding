package com.brainserve.onboarding.auth.infrastructure.persistence;

import com.brainserve.onboarding.auth.domain.model.ClientSecurityToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ClientSecurityTokenRepository extends JpaRepository<ClientSecurityToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ClientSecurityToken t where t.tokenHash=:hash")
    Optional<ClientSecurityToken> findByTokenHashForUpdate(String hash);

    @Modifying
    @Query("update ClientSecurityToken t set t.revokedAt=CURRENT_TIMESTAMP where t.organizationId=:organizationId and t.userId=:userId and t.tokenType=:type and t.consumedAt is null and t.revokedAt is null")
    int revokeOutstanding(UUID organizationId, UUID userId, String type);
}
