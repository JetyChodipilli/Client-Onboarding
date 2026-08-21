package com.brainserve.onboarding.workflow.infrastructure.persistence;

import com.brainserve.onboarding.workflow.domain.model.WorkflowTemplateStep;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowTemplateStepRepository extends JpaRepository<WorkflowTemplateStep, UUID> {
    List<WorkflowTemplateStep> findAllByOrganizationIdAndTemplateVersionIdOrderByDisplayOrderAscIdAsc(UUID organizationId, UUID templateVersionId);
    void deleteAllByOrganizationIdAndTemplateVersionId(UUID organizationId, UUID templateVersionId);
    long countByOrganizationIdAndTemplateVersionId(UUID organizationId, UUID templateVersionId);

    @Query("select s.templateVersionId as versionId, count(s) as stepCount from WorkflowTemplateStep s " +
           "where s.organizationId=:organizationId and s.templateVersionId in :versionIds group by s.templateVersionId")
    List<StepCount> countByVersionIds(@Param("organizationId") UUID organizationId,
                                      @Param("versionIds") Collection<UUID> versionIds);

    interface StepCount {
        UUID getVersionId();
        long getStepCount();
    }
}
