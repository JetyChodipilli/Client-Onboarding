package com.brainserve.clientonboarding.workflow.domain.repository;

import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateVersion;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTemplateRepository {
    PageSlice<WorkflowTemplate> findPage(UUID organizationId, String search, int page, int size);
    Optional<WorkflowTemplate> findTemplate(UUID organizationId, UUID templateId);
    boolean lockActiveTemplate(UUID organizationId, UUID templateId);
    WorkflowTemplate insertTemplate(WorkflowTemplate template, UUID actorId);
    boolean archiveTemplate(UUID organizationId, UUID templateId, long version, UUID actorId, Instant now);
    List<TemplateVersion> findVersions(UUID organizationId, UUID templateId);
    Optional<TemplateVersion> findVersion(UUID organizationId, UUID versionId);
    int nextVersionNumber(UUID organizationId, UUID templateId);
    TemplateVersion insertVersion(TemplateVersion version, UUID actorId);
    List<TemplateStep> findSteps(UUID organizationId, UUID versionId);
    boolean replaceDraftSteps(UUID organizationId, UUID versionId, long expectedVersion,
                              List<TemplateStep> steps, UUID actorId, Instant now);
    boolean publishVersion(UUID organizationId, UUID versionId, long version, UUID actorId, Instant now);
}
