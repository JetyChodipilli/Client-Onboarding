package com.brainserve.onboarding.auth.infrastructure.persistence;

import com.brainserve.onboarding.auth.domain.model.SecurityToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SecurityTokenRepository extends JpaRepository<SecurityToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SecurityToken t where t.tokenHash = :tokenHash and t.tokenType = :tokenType")
    Optional<SecurityToken> findByTokenHashAndTokenTypeForUpdate(String tokenHash, String tokenType);

    @Modifying
    @Query("update SecurityToken t set t.revokedAt = CURRENT_TIMESTAMP where t.userId = :userId and t.tokenType = :type and t.consumedAt is null and t.revokedAt is null")
    int revokeOutstanding(UUID userId, String type);
}
