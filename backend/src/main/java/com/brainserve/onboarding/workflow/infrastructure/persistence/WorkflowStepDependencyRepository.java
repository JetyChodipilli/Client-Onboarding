package com.brainserve.onboarding.workflow.infrastructure.persistence;

import com.brainserve.onboarding.workflow.domain.model.WorkflowStepDependency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStepDependencyRepository extends JpaRepository<WorkflowStepDependency, WorkflowStepDependency.Key> {
    List<WorkflowStepDependency> findAllByOrganizationIdAndTemplateVersionId(UUID organizationId, UUID templateVersionId);
    void deleteAllByOrganizationIdAndTemplateVersionId(UUID organizationId, UUID templateVersionId);
}
