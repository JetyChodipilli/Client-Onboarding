package com.brainserve.onboarding.servicecatalog.infrastructure.persistence;

import com.brainserve.onboarding.servicecatalog.domain.model.ServiceDefinition;
import com.brainserve.onboarding.servicecatalog.domain.model.ServiceStatus;
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

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, UUID> {
    Optional<ServiceDefinition> findByOrganizationIdAndId(UUID organizationId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from ServiceDefinition s where s.organizationId=:organizationId and s.id=:id")
    Optional<ServiceDefinition> findForRelationshipValidation(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
    @Query("select s from ServiceDefinition s where s.organizationId=:organizationId and s.status=:status and (lower(s.name) like :searchPrefix escape '\\' or lower(s.code) like :searchPrefix escape '\\')")
    Page<ServiceDefinition> searchActive(@Param("organizationId") UUID organizationId, @Param("status") ServiceStatus status,
                                         @Param("searchPrefix") String searchPrefix, Pageable pageable);

    @Query("select s from ServiceDefinition s where s.organizationId=:organizationId and (lower(s.name) like :searchPrefix escape '\\' or lower(s.code) like :searchPrefix escape '\\')")
    Page<ServiceDefinition> searchAll(@Param("organizationId") UUID organizationId, @Param("searchPrefix") String searchPrefix,
                                      Pageable pageable);
    Page<ServiceDefinition> findAllByOrganizationIdAndStatus(UUID organizationId, ServiceStatus status, Pageable pageable);
    Page<ServiceDefinition> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
    List<ServiceDefinition> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}
