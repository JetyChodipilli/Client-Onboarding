package com.brainserve.onboarding.workflow.infrastructure.persistence;

import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplate;
import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, UUID> {
    Optional<WorkflowTemplate> findByOrganizationIdAndId(UUID organizationId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WorkflowTemplate t where t.organizationId=:organizationId and t.id=:id")
    Optional<WorkflowTemplate> findForUpdate(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
    Page<WorkflowTemplate> findAllByOrganizationIdAndStatus(UUID organizationId, WorkflowTemplateStatus status, Pageable pageable);
    Page<WorkflowTemplate> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    boolean existsByOrganizationIdAndNameIgnoreCaseAndStatus(UUID organizationId, String name, WorkflowTemplateStatus status);
    boolean existsByOrganizationIdAndNameIgnoreCaseAndStatusAndIdNot(UUID organizationId, String name, WorkflowTemplateStatus status, UUID id);
}
