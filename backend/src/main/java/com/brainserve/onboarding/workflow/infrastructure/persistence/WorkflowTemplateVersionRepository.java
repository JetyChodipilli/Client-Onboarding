package com.brainserve.onboarding.workflow.infrastructure.persistence;

import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateVersion;
import com.brainserve.onboarding.workflow.domain.model.WorkflowVersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WorkflowTemplateVersionRepository extends JpaRepository<WorkflowTemplateVersion, UUID> {
    Optional<WorkflowTemplateVersion> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<WorkflowTemplateVersion> findByOrganizationIdAndIdAndStatus(UUID organizationId, UUID id, WorkflowVersionStatus status);
    List<WorkflowTemplateVersion> findAllByOrganizationIdAndTemplateIdOrderByVersionNumberDesc(UUID organizationId, UUID templateId);
    List<WorkflowTemplateVersion> findAllByOrganizationIdAndTemplateIdIn(UUID organizationId, Collection<UUID> templateIds);
    Optional<WorkflowTemplateVersion> findFirstByOrganizationIdAndTemplateIdAndStatusOrderByVersionNumberDesc(UUID organizationId, UUID templateId, WorkflowVersionStatus status);
    boolean existsByOrganizationIdAndTemplateIdAndStatus(UUID organizationId, UUID templateId, WorkflowVersionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from WorkflowTemplateVersion v where v.organizationId=:organizationId and v.id=:id")
    Optional<WorkflowTemplateVersion> findForUpdate(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
}
