package com.brainserve.onboarding.client.infrastructure.persistence;

import com.brainserve.onboarding.client.domain.model.ClientContact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientContactRepository extends JpaRepository<ClientContact, UUID> {
    Optional<ClientContact> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Page<ClientContact> findAllByOrganizationIdAndClientId(UUID organizationId, UUID clientId, Pageable pageable);
    boolean existsByOrganizationIdAndClientIdAndNormalizedEmailAndIdNot(UUID organizationId, UUID clientId, String normalizedEmail, UUID id);
    boolean existsByOrganizationIdAndClientIdAndNormalizedEmail(UUID organizationId, UUID clientId, String normalizedEmail);
}
