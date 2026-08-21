package com.brainserve.onboarding.auth.infrastructure.persistence;

import com.brainserve.onboarding.auth.domain.model.AuthSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.refreshTokenHash = :hash")
    Optional<AuthSession> findByRefreshTokenHashForUpdate(String hash);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = CURRENT_TIMESTAMP, s.revokeReason = :reason " +
           "where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(UUID userId, String reason);
}
