package com.brainserve.onboarding.project.infrastructure.persistence;

import com.brainserve.onboarding.project.domain.model.Project;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
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

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByOrganizationIdAndId(UUID organizationId, UUID id);
    List<Project> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.organizationId=:organizationId and p.id=:id")
    Optional<Project> findForUpdate(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
    @Query(value = """
            select p.*
            from client_onboarding.projects p
            join client_onboarding.clients c
              on c.organization_id = p.organization_id and c.id = p.client_id
            join client_onboarding.services s
              on s.organization_id = p.organization_id and s.id = p.service_id
            where p.organization_id = :organizationId
              and (cast(:clientId as uuid) is null or p.client_id = cast(:clientId as uuid))
              and ((cast(:statusName as varchar) is not null and p.status = cast(:statusName as varchar))
                   or (cast(:statusName as varchar) is null and (:includeArchived = true or p.status <> 'ARCHIVED')))
              and (lower(p.name) like :searchPrefix escape '\\'
                   or lower(c.name) like :searchPrefix escape '\\'
                   or lower(s.name) like :searchPrefix escape '\\'
                   or lower(s.code) like :searchPrefix escape '\\')
            order by p.created_at desc, p.id desc
            """,
            countQuery = """
            select count(*)
            from client_onboarding.projects p
            join client_onboarding.clients c
              on c.organization_id = p.organization_id and c.id = p.client_id
            join client_onboarding.services s
              on s.organization_id = p.organization_id and s.id = p.service_id
            where p.organization_id = :organizationId
              and (cast(:clientId as uuid) is null or p.client_id = cast(:clientId as uuid))
              and ((cast(:statusName as varchar) is not null and p.status = cast(:statusName as varchar))
                   or (cast(:statusName as varchar) is null and (:includeArchived = true or p.status <> 'ARCHIVED')))
              and (lower(p.name) like :searchPrefix escape '\\'
                   or lower(c.name) like :searchPrefix escape '\\'
                   or lower(s.name) like :searchPrefix escape '\\'
                   or lower(s.code) like :searchPrefix escape '\\')
            """,
            nativeQuery = true)
    Page<Project> search(@Param("organizationId") UUID organizationId,
                         @Param("clientId") UUID clientId,
                         @Param("statusName") String statusName,
                         @Param("includeArchived") boolean includeArchived,
                         @Param("searchPrefix") String searchPrefix,
                         Pageable pageable);
    Page<Project> findAllByOrganizationIdAndStatusNot(UUID organizationId, ProjectStatus excluded, Pageable pageable);
    Page<Project> findAllByOrganizationIdAndStatus(UUID organizationId, ProjectStatus status, Pageable pageable);
    Page<Project> findAllByOrganizationIdAndClientIdAndStatusNot(UUID organizationId, UUID clientId, ProjectStatus excluded, Pageable pageable);
    Page<Project> findAllByOrganizationIdAndClientIdAndStatus(UUID organizationId, UUID clientId, ProjectStatus status, Pageable pageable);
    Page<Project> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    Page<Project> findAllByOrganizationIdAndClientId(UUID organizationId, UUID clientId, Pageable pageable);
}
