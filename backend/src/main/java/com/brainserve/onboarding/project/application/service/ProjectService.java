package com.brainserve.onboarding.project.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientLookupService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.organization.application.service.OrganizationMemberAccessService;
import com.brainserve.onboarding.project.api.request.AddProjectMemberRequest;
import com.brainserve.onboarding.project.api.request.CreateProjectRequest;
import com.brainserve.onboarding.project.api.request.ProjectVersionRequest;
import com.brainserve.onboarding.project.api.request.UpdateProjectRequest;
import com.brainserve.onboarding.project.api.response.ProjectMemberResponse;
import com.brainserve.onboarding.project.api.response.ProjectResponse;
import com.brainserve.onboarding.project.domain.model.Project;
import com.brainserve.onboarding.project.domain.model.ProjectMember;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.project.infrastructure.persistence.ProjectMemberRepository;
import com.brainserve.onboarding.project.infrastructure.persistence.ProjectRepository;
import com.brainserve.onboarding.servicecatalog.application.service.ServiceCatalogLookupService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<ProjectStatus> MEMBER_EDITABLE_STATUSES = Set.of(
            ProjectStatus.DRAFT, ProjectStatus.ONBOARDING, ProjectStatus.READY, ProjectStatus.ACTIVE, ProjectStatus.ON_HOLD);

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ClientLookupService clientLookup;
    private final ServiceCatalogLookupService serviceLookup;
    private final OrganizationMemberAccessService organizationMembers;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final Clock clock;

    public ProjectService(ProjectRepository projects,
                          ProjectMemberRepository members,
                          ClientLookupService clientLookup,
                          ServiceCatalogLookupService serviceLookup,
                          OrganizationMemberAccessService organizationMembers,
                          ActivityTimelineService activity,
                          AuditService audit,
                          Clock clock) {
        this.projects = projects;
        this.members = members;
        this.clientLookup = clientLookup;
        this.serviceLookup = serviceLookup;
        this.organizationMembers = organizationMembers;
        this.activity = activity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ProjectResponse create(TenantPrincipal principal, CreateProjectRequest request, HttpServletRequest servletRequest) {
        var client = clientLookup.requireUsableForRelationshipWrite(principal.organizationId(), request.clientId());
        var service = serviceLookup.requireActiveForRelationshipWrite(principal.organizationId(), request.serviceId());
        Instant now = clock.instant();
        Project project = projects.saveAndFlush(new Project(UUID.randomUUID(), principal.organizationId(), client.id(), service.id(),
                request.name(), request.description(), principal.userId(), now));
        activity.record(principal.organizationId(), client.id(), project.getId(), principal.userId(), "PROJECT_CREATED",
                "PROJECT", project.getId(), "Project created", Map.of("status", project.getStatus().name()));
        audit.record(principal.organizationId(), principal.userId(), "PROJECT_CREATED", "PROJECT", project.getId(), null,
                Map.of("name", project.getName(), "clientId", client.id(), "serviceId", service.id(), "status", project.getStatus()), servletRequest);
        return map(project, client.name(), service.name());
    }

    @Transactional(readOnly = true)
    public PageResult<ProjectResponse> list(TenantPrincipal principal, int page, int size, ProjectStatus status,
                                            UUID clientId, String query, boolean includeArchived) {
        int safePage = Math.max(page, 0);
        int pageSize = safeSize(size);
        var pageable = PageRequest.of(safePage, pageSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        String searchPrefix = searchPrefix(query);
        Page<Project> result;
        if (searchPrefix != null) {
            result = projects.search(principal.organizationId(), clientId, status == null ? null : status.name(),
                    includeArchived, searchPrefix, PageRequest.of(safePage, pageSize));
        } else if (clientId != null && status != null) {
            result = projects.findAllByOrganizationIdAndClientIdAndStatus(principal.organizationId(), clientId, status, pageable);
        } else if (clientId != null && includeArchived) {
            result = projects.findAllByOrganizationIdAndClientId(principal.organizationId(), clientId, pageable);
        } else if (clientId != null) {
            result = projects.findAllByOrganizationIdAndClientIdAndStatusNot(principal.organizationId(), clientId, ProjectStatus.ARCHIVED, pageable);
        } else if (status != null) {
            result = projects.findAllByOrganizationIdAndStatus(principal.organizationId(), status, pageable);
        } else if (includeArchived) {
            result = projects.findAllByOrganizationId(principal.organizationId(), pageable);
        } else {
            result = projects.findAllByOrganizationIdAndStatusNot(principal.organizationId(), ProjectStatus.ARCHIVED, pageable);
        }
        List<Project> content = result.getContent();
        Map<UUID, String> clientNames = clientLookup.names(principal.organizationId(), content.stream().map(Project::getClientId).collect(Collectors.toSet()));
        Map<UUID, String> serviceNames = serviceLookup.names(principal.organizationId(), content.stream().map(Project::getServiceId).collect(Collectors.toSet()));
        List<ProjectResponse> items = content.stream().map(project -> map(project,
                clientNames.get(project.getClientId()), serviceNames.get(project.getServiceId()))).toList();
        return new PageResult<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(TenantPrincipal principal, UUID projectId) {
        Project project = requireProject(principal.organizationId(), projectId);
        Map<UUID, String> clientNames = clientLookup.names(principal.organizationId(), Set.of(project.getClientId()));
        Map<UUID, String> serviceNames = serviceLookup.names(principal.organizationId(), Set.of(project.getServiceId()));
        return map(project, clientNames.get(project.getClientId()), serviceNames.get(project.getServiceId()));
    }

    @Transactional
    public ProjectResponse update(TenantPrincipal principal, UUID projectId, UpdateProjectRequest request, HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireVersion(project.getVersion(), request.version());
        String clientName;
        String serviceName;
        if (project.getStatus() == ProjectStatus.DRAFT) {
            var client = clientLookup.requireUsableForRelationshipWrite(principal.organizationId(), request.clientId());
            var service = serviceLookup.requireActiveForRelationshipWrite(principal.organizationId(), request.serviceId());
            clientName = client.name();
            serviceName = service.name();
        } else {
            if (!project.getClientId().equals(request.clientId()) || !project.getServiceId().equals(request.serviceId())) {
                throw invalidState("Client and service cannot change after onboarding has started");
            }
            clientName = clientLookup.names(principal.organizationId(), Set.of(project.getClientId())).get(project.getClientId());
            serviceName = serviceLookup.names(principal.organizationId(), Set.of(project.getServiceId())).get(project.getServiceId());
            if (clientName == null || serviceName == null) throw notFound();
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", project.getName());
        before.put("clientId", project.getClientId());
        before.put("serviceId", project.getServiceId());
        before.put("version", project.getVersion());
        try {
            project.update(request.clientId(), request.serviceId(), request.name(), request.description(), principal.userId(), clock.instant());
        } catch (IllegalStateException ex) {
            throw invalidState(ex.getMessage());
        }
        projects.saveAndFlush(project);
        activity.record(principal.organizationId(), project.getClientId(), project.getId(), principal.userId(), "PROJECT_UPDATED",
                "PROJECT", project.getId(), "Project details updated", Map.of("version", project.getVersion()));
        audit.record(principal.organizationId(), principal.userId(), "PROJECT_UPDATED", "PROJECT", project.getId(), before,
                Map.of("name", project.getName(), "clientId", project.getClientId(), "serviceId", project.getServiceId(), "version", project.getVersion()), servletRequest);
        return map(project, clientName, serviceName);
    }

    @Transactional
    public ProjectResponse cancel(TenantPrincipal principal, UUID projectId, ProjectVersionRequest request, HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireVersion(project.getVersion(), request.version());
        ProjectStatus before = project.getStatus();
        try {
            project.cancel(principal.userId(), clock.instant());
        } catch (IllegalStateException ex) {
            throw invalidState(ex.getMessage());
        }
        projects.saveAndFlush(project);
        stateActivityAndAudit(principal, project, "PROJECT_CANCELLED", "Project cancelled", before, servletRequest);
        return responseWithNames(principal.organizationId(), project);
    }

    @Transactional
    public ProjectResponse hold(TenantPrincipal principal, UUID projectId, ProjectVersionRequest request, HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireVersion(project.getVersion(), request.version());
        ProjectStatus before = project.getStatus();
        try {
            project.hold(principal.userId(), clock.instant());
        } catch (IllegalStateException ex) {
            throw invalidState(ex.getMessage());
        }
        projects.saveAndFlush(project);
        stateActivityAndAudit(principal, project, "PROJECT_ON_HOLD", "Project put on hold", before, servletRequest);
        return responseWithNames(principal.organizationId(), project);
    }

    @Transactional
    public ProjectResponse resume(TenantPrincipal principal, UUID projectId, ProjectVersionRequest request, HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireVersion(project.getVersion(), request.version());
        ProjectStatus before = project.getStatus();
        try {
            project.resume(principal.userId(), clock.instant());
        } catch (IllegalStateException ex) {
            throw invalidState(ex.getMessage());
        }
        projects.saveAndFlush(project);
        stateActivityAndAudit(principal, project, "PROJECT_RESUMED", "Project resumed", before, servletRequest);
        return responseWithNames(principal.organizationId(), project);
    }

    @Transactional
    public ProjectResponse archive(TenantPrincipal principal, UUID projectId, ProjectVersionRequest request, HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireVersion(project.getVersion(), request.version());
        ProjectStatus before = project.getStatus();
        try {
            project.archive(principal.userId(), clock.instant());
        } catch (IllegalStateException ex) {
            throw invalidState(ex.getMessage());
        }
        projects.saveAndFlush(project);
        stateActivityAndAudit(principal, project, "PROJECT_ARCHIVED", "Project archived", before, servletRequest);
        return responseWithNames(principal.organizationId(), project);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(TenantPrincipal principal, UUID projectId) {
        requireProject(principal.organizationId(), projectId);
        return members.findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(principal.organizationId(), projectId)
                .stream().map(ProjectService::map).toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(TenantPrincipal principal, UUID projectId, AddProjectMemberRequest request,
                                           HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireMemberEditable(project);
        if (!organizationMembers.isActiveMembership(principal.organizationId(), request.organizationUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_MEMBER", "The selected organization member is not active.");
        }
        if (members.existsByOrganizationIdAndProjectIdAndOrganizationUserId(
                principal.organizationId(), projectId, request.organizationUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_MEMBER_EXISTS", "This person is already a project member.");
        }
        ProjectMember member = members.saveAndFlush(new ProjectMember(UUID.randomUUID(), principal.organizationId(), projectId,
                request.organizationUserId(), request.responsibility(), principal.userId(), clock.instant()));
        activity.record(principal.organizationId(), project.getClientId(), projectId, principal.userId(), "PROJECT_MEMBER_ADDED",
                "PROJECT_MEMBER", member.getId(), "Project member added", Map.of("organizationUserId", member.getOrganizationUserId()));
        audit.record(principal.organizationId(), principal.userId(), "PROJECT_MEMBER_ADDED", "PROJECT_MEMBER", member.getId(), null,
                Map.of("projectId", projectId, "organizationUserId", member.getOrganizationUserId()), servletRequest);
        return map(member);
    }

    @Transactional
    public void removeMember(TenantPrincipal principal, UUID projectId, UUID memberId, HttpServletRequest servletRequest) {
        Project project = requireProject(principal.organizationId(), projectId);
        requireMemberEditable(project);
        ProjectMember member = members.findByOrganizationIdAndId(principal.organizationId(), memberId)
                .filter(value -> value.getProjectId().equals(projectId))
                .orElseThrow(ProjectService::notFound);
        members.delete(member);
        members.flush();
        activity.record(principal.organizationId(), project.getClientId(), projectId, principal.userId(), "PROJECT_MEMBER_REMOVED",
                "PROJECT_MEMBER", memberId, "Project member removed", Map.of("organizationUserId", member.getOrganizationUserId()));
        audit.record(principal.organizationId(), principal.userId(), "PROJECT_MEMBER_REMOVED", "PROJECT_MEMBER", memberId,
                Map.of("projectId", projectId, "organizationUserId", member.getOrganizationUserId()), null, servletRequest);
    }

    @Transactional(readOnly = true)
    public ActivityTimelineService.PageResult activity(TenantPrincipal principal, UUID projectId, int page, int size) {
        requireProject(principal.organizationId(), projectId);
        return activity.listByProject(principal.organizationId(), projectId, page, size);
    }

    private Project requireProject(UUID organizationId, UUID projectId) {
        return projects.findByOrganizationIdAndId(organizationId, projectId).orElseThrow(ProjectService::notFound);
    }

    private void stateActivityAndAudit(TenantPrincipal principal, Project project, String action, String summary,
                                       ProjectStatus before, HttpServletRequest request) {
        activity.record(principal.organizationId(), project.getClientId(), project.getId(), principal.userId(), action,
                "PROJECT", project.getId(), summary, Map.of("from", before.name(), "to", project.getStatus().name()));
        audit.record(principal.organizationId(), principal.userId(), action, "PROJECT", project.getId(),
                Map.of("status", before), Map.of("status", project.getStatus()), request);
    }

    private ProjectResponse responseWithNames(UUID organizationId, Project project) {
        Map<UUID, String> clientNames = clientLookup.names(organizationId, Set.of(project.getClientId()));
        Map<UUID, String> serviceNames = serviceLookup.names(organizationId, Set.of(project.getServiceId()));
        return map(project, clientNames.get(project.getClientId()), serviceNames.get(project.getServiceId()));
    }

    private static void requireMemberEditable(Project project) {
        if (!MEMBER_EDITABLE_STATUSES.contains(project.getStatus())) {
            throw invalidState("Project membership cannot be changed in the current project state.");
        }
    }

    private static String searchPrefix(String query) {
        if (query == null || query.isBlank()) return null;
        String normalized = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return normalized.isBlank() ? null : normalized + "%";
    }

    private static int safeSize(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }
    private static void requireVersion(long actual, long requested) { if (actual != requested) throw conflict(); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource was not found."); }
    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The resource changed. Refresh and try again."); }
    private static ApiException invalidState(String message) { return new ApiException(HttpStatus.CONFLICT, "PROJECT_STATE_INVALID", message); }

    private static ProjectResponse map(Project project, String clientName, String serviceName) {
        return new ProjectResponse(project.getId(), project.getClientId(), clientName, project.getServiceId(), serviceName,
                project.getName(), project.getDescription(), project.getStatus(), project.getArchivedAt(), project.getCreatedAt(), project.getUpdatedAt(), project.getVersion());
    }

    private static ProjectMemberResponse map(ProjectMember member) {
        return new ProjectMemberResponse(member.getId(), member.getProjectId(), member.getOrganizationUserId(), member.getResponsibility(), member.getCreatedAt());
    }

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
