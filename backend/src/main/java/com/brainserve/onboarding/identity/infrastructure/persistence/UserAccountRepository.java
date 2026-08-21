package com.brainserve.onboarding.identity.infrastructure.persistence;

import com.brainserve.onboarding.identity.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :userId")
    Optional<UserAccount> findByIdForUpdate(UUID userId);
}
