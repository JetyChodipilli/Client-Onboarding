package com.brainserve.onboarding.project.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.project.domain.model.Project;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.project.infrastructure.persistence.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Narrow project application boundary owned by the project module for onboarding orchestration. */
@Service
public class ProjectWorkflowAccessService {
    private final ProjectRepository projects;

    public ProjectWorkflowAccessService(ProjectRepository projects) { this.projects = projects; }

    @Transactional
    public ProjectRef transitionToOnboarding(UUID organizationId, UUID projectId, long expectedVersion, UUID actorId, Instant now) {
        Project project = projects.findForUpdate(organizationId, projectId).orElseThrow(ProjectWorkflowAccessService::notFound);
        if (project.getVersion() != expectedVersion) throw conflict();
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_STATE_INVALID", "Only draft projects can start onboarding.");
        }
        try { project.transitionToOnboarding(actorId, now); }
        catch (IllegalStateException ex) { throw new ApiException(HttpStatus.CONFLICT, "PROJECT_STATE_INVALID", ex.getMessage()); }
        projects.saveAndFlush(project);
        return map(project);
    }

    @Transactional(readOnly = true)
    public ProjectRef require(UUID organizationId, UUID projectId) {
        return map(projects.findByOrganizationIdAndId(organizationId, projectId).orElseThrow(ProjectWorkflowAccessService::notFound));
    }

    private static ProjectRef map(Project project) {
        return new ProjectRef(project.getId(), project.getClientId(), project.getServiceId(), project.getName(), project.getStatus(), project.getVersion());
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource was not found."); }
    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The project changed. Refresh and try again."); }

    public record ProjectRef(UUID id, UUID clientId, UUID serviceId, String name, ProjectStatus status, long version) {}
}
