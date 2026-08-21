package com.brainserve.onboarding.client.infrastructure.persistence;

import com.brainserve.onboarding.client.domain.model.Client;
import com.brainserve.onboarding.client.domain.model.ClientStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByOrganizationIdAndId(UUID organizationId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Client c where c.organizationId=:organizationId and c.id=:id")
    Optional<Client> findForRelationshipValidation(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
    @Query("select c from Client c where c.organizationId=:organizationId and c.status<>:excluded and lower(c.name) like :searchPrefix escape '\\'")
    Page<Client> searchActive(@Param("organizationId") UUID organizationId, @Param("excluded") ClientStatus excluded,
                              @Param("searchPrefix") String searchPrefix, Pageable pageable);

    @Query("select c from Client c where c.organizationId=:organizationId and c.status=:status and lower(c.name) like :searchPrefix escape '\\'")
    Page<Client> searchByStatus(@Param("organizationId") UUID organizationId, @Param("status") ClientStatus status,
                                @Param("searchPrefix") String searchPrefix, Pageable pageable);

    @Query("select c from Client c where c.organizationId=:organizationId and lower(c.name) like :searchPrefix escape '\\'")
    Page<Client> searchAll(@Param("organizationId") UUID organizationId, @Param("searchPrefix") String searchPrefix,
                           Pageable pageable);
    Page<Client> findAllByOrganizationIdAndStatusNot(UUID organizationId, ClientStatus excluded, Pageable pageable);
    Page<Client> findAllByOrganizationIdAndStatus(UUID organizationId, ClientStatus status, Pageable pageable);
    Page<Client> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    List<Client> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}
