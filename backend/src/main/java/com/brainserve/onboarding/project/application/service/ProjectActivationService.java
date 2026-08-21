package com.brainserve.onboarding.project.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.project.domain.model.Project;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.project.infrastructure.persistence.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controlled project-module boundary for Phase 11 readiness and activation transitions. */
@Service
public class ProjectActivationService {
    private final ProjectRepository projects;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public ProjectActivationService(ProjectRepository projects, ActivityTimelineService activity,
                                    AuditService audit, OutboxService outbox, Clock clock) {
        this.projects = projects;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Called only by the onboarding final-review application boundary in the same transaction. */
    @Transactional
    public ProjectRef markReadyFromCompletedOnboarding(UUID organizationId, UUID projectId, UUID actorId) {
        Project project = projects.findForUpdate(organizationId, projectId).orElseThrow(ProjectActivationService::notFound);
        ProjectStatus before = project.getStatus();
        Instant now = clock.instant();
        try {
            project.transitionToReady(actorId, now);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_STATE_INVALID", ex.getMessage());
        }
        projects.saveAndFlush(project);
        activity.record(organizationId, project.getClientId(), projectId, actorId, "PROJECT_READY",
                "PROJECT", projectId, "Project is ready for activation", Map.of("from", before.name(), "to", project.getStatus().name()));
        audit.recordApplication(organizationId, actorId, "INTERNAL", "PROJECT_READY", "PROJECT", projectId,
                Map.of("status", before), Map.of("status", project.getStatus(), "version", project.getVersion()));
        outbox.record(organizationId, "PROJECT_READY", "PROJECT", projectId,
                Map.of("projectId", projectId, "status", project.getStatus().name()));
        return map(project);
    }

    @Transactional
    public ProjectRef activate(TenantPrincipal principal, UUID projectId, long expectedVersion, HttpServletRequest request) {
        Project project = projects.findForUpdate(principal.organizationId(), projectId).orElseThrow(ProjectActivationService::notFound);
        if (project.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The project changed. Refresh and try again.");
        }
        ProjectStatus before = project.getStatus();
        Instant now = clock.instant();
        try {
            project.activate(principal.userId(), now);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_STATE_INVALID", ex.getMessage());
        }
        projects.saveAndFlush(project);
        activity.record(principal.organizationId(), project.getClientId(), projectId, principal.userId(), "PROJECT_ACTIVATED",
                "PROJECT", projectId, "Project activated", Map.of("from", before.name(), "to", project.getStatus().name()));
        audit.record(principal.organizationId(), principal.userId(), "PROJECT_ACTIVATED", "PROJECT", projectId,
                Map.of("status", before), Map.of("status", project.getStatus(), "version", project.getVersion()), request);
        outbox.record(principal.organizationId(), "PROJECT_ACTIVATED", "PROJECT", projectId,
                Map.of("projectId", projectId, "status", project.getStatus().name()));
        return map(project);
    }

    @Transactional(readOnly = true)
    public ProjectRef require(UUID organizationId, UUID projectId) {
        return map(projects.findByOrganizationIdAndId(organizationId, projectId).orElseThrow(ProjectActivationService::notFound));
    }

    private static ProjectRef map(Project project) {
        return new ProjectRef(project.getId(), project.getClientId(), project.getStatus(), project.getVersion());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested project was not found.");
    }

    public record ProjectRef(UUID id, UUID clientId, ProjectStatus status, long version) {}
}
