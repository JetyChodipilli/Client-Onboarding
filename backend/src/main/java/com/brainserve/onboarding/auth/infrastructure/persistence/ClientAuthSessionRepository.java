package com.brainserve.onboarding.auth.infrastructure.persistence;

import com.brainserve.onboarding.auth.domain.model.ClientAuthSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ClientAuthSessionRepository extends JpaRepository<ClientAuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClientAuthSession s where s.refreshTokenHash=:hash")
    Optional<ClientAuthSession> findByRefreshTokenHashForUpdate(String hash);

    @Modifying
    @Query("update ClientAuthSession s set s.revokedAt=CURRENT_TIMESTAMP, s.revokeReason=:reason where s.userId=:userId and s.revokedAt is null")
    int revokeAllByUserId(UUID userId, String reason);
}
