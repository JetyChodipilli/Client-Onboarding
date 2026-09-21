package com.brainserve.clientonboarding.project.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.client.domain.repository.ClientRepository;
import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAdminRepository;
import com.brainserve.clientonboarding.project.domain.model.ActivityEntry;
import com.brainserve.clientonboarding.project.domain.model.ProjectMember;
import com.brainserve.clientonboarding.project.domain.model.ProjectRecord;
import com.brainserve.clientonboarding.project.domain.repository.ProjectRepository;
import com.brainserve.clientonboarding.servicecatalog.domain.repository.ServiceCatalogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService implements ProjectWorkflowPort {
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final ServiceCatalogRepository services;
    private final OrganizationAdminRepository organizations;
    private final AuditService audit;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ClientRepository clients, ServiceCatalogRepository services,
                          OrganizationAdminRepository organizations, AuditService audit, Clock clock) {
        this.projects = projects; this.clients = clients; this.services = services;
        this.organizations = organizations; this.audit = audit; this.clock = clock;
    }

    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public PageSlice<ProjectRecord> list(TenantPrincipal principal, String search, String status, UUID clientId,
                                         int page, int size) {
        return projects.findPage(principal.organizationId(), search == null ? "" : search.trim(),
                optionalStatus(status), clientId, validPage(page), validSize(size));
    }

    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ProjectRecord get(TenantPrincipal principal, UUID projectId) {
        return requireProject(principal.organizationId(), projectId);
    }

    @Override
    public ProjectRecord requireProject(UUID organizationId, UUID projectId) {
        return projects.findById(organizationId, projectId).orElseThrow(this::notFound);
    }

    @PreAuthorize("hasAuthority('PROJECT_CREATE')")
    @Transactional
    public ProjectRecord create(TenantPrincipal principal, ProjectCommand command, RequestMetadata metadata) {
        var client = clients.findById(principal.organizationId(), command.clientId()).orElseThrow(this::notFound);
        var service = services.findById(principal.organizationId(), command.serviceId()).orElseThrow(this::notFound);
        if (client.archivedAt() != null || service.archivedAt() != null
                || service.status() != com.brainserve.clientonboarding.servicecatalog.domain.model.ServiceDefinition.Status.ACTIVE) {
            throw new DomainException("PROJECT_RELATIONSHIP_INVALID",
                    "Project client and service must be available in this organization.", HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        ProjectRecord project = new ProjectRecord(UUID.randomUUID(), principal.organizationId(), client.id(),
                client.name(), client.status().name(), service.id(), service.name(), service.code(), required(command.name(), 180),
                optional(command.description(), 2000), ProjectRecord.Status.DRAFT, nonNegative(command.valueMinor()),
                currency(command.currencyCode()), command.targetStartDate(), null, null, now, now, 0);
        projects.insert(project, principal.userId());
        activity(principal.organizationId(), project.id(), principal.userId(), "PROJECT_CREATED", project.id(),
                "Project created in DRAFT status.", now);
        audit.append(principal.organizationId(), principal.userId(), "PROJECT_CREATED", "PROJECT", project.id(),
                Map.of(), Map.of("name", project.name(), "status", project.status()), "API", metadata.ipHash());
        return project;
    }

    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    @Transactional
    public ProjectRecord update(TenantPrincipal principal, UUID id, ProjectCommand command,
                                RequestMetadata metadata) {
        ProjectRecord current = requireProject(principal.organizationId(), id);
        var client = clients.findById(principal.organizationId(), command.clientId()).orElseThrow(this::notFound);
        var service = services.findById(principal.organizationId(), command.serviceId()).orElseThrow(this::notFound);
        if (client.archivedAt() != null || service.archivedAt() != null
                || service.status() != com.brainserve.clientonboarding.servicecatalog.domain.model.ServiceDefinition.Status.ACTIVE) {
            throw new DomainException("PROJECT_RELATIONSHIP_INVALID",
                    "Project client and service must be available in this organization.", HttpStatus.CONFLICT);
        }
        if (current.status() != ProjectRecord.Status.DRAFT
                && (!current.clientId().equals(command.clientId()) || !current.serviceId().equals(command.serviceId()))) {
            throw new DomainException("PROJECT_RELATIONSHIP_IMMUTABLE",
                    "Client and service cannot change after onboarding begins.", HttpStatus.CONFLICT);
        }
        ProjectRecord replacement = new ProjectRecord(id, principal.organizationId(), client.id(), client.name(),
                client.status().name(), service.id(), service.name(), service.code(), required(command.name(), 180),
                optional(command.description(), 2000), current.status(), nonNegative(command.valueMinor()),
                currency(command.currencyCode()), command.targetStartDate(), current.previousStatus(),
                current.archivedAt(), current.createdAt(), clock.instant(), current.version());
        if (!projects.update(principal.organizationId(), id, replacement, command.version(), principal.userId(),
                clock.instant())) throw conflict();
        activity(principal.organizationId(), id, principal.userId(), "PROJECT_UPDATED", id,
                "Project details updated.", clock.instant());
        audit.append(principal.organizationId(), principal.userId(), "PROJECT_UPDATED", "PROJECT", id,
                Map.of("name", current.name()), Map.of("name", replacement.name()), "API", metadata.ipHash());
        return projects.findById(principal.organizationId(), id).orElseThrow();
    }

    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    @Transactional
    public ProjectRecord transition(TenantPrincipal principal, UUID id, String action, long version,
                                    RequestMetadata metadata) {
        ProjectRecord current = requireProject(principal.organizationId(), id);
        ProjectRecord.Status next = nextStatus(current, action);
        ProjectRecord.Status previous = next == ProjectRecord.Status.ON_HOLD ? current.status()
                : (next == ProjectRecord.Status.ARCHIVED ? current.status() : current.previousStatus());
        if (!projects.transition(principal.organizationId(), id, current.status(), next, previous, version,
                principal.userId(), clock.instant())) throw conflict();
        activity(principal.organizationId(), id, principal.userId(), "PROJECT_STATUS_CHANGED", id,
                current.status() + " → " + next, clock.instant());
        audit.append(principal.organizationId(), principal.userId(), "PROJECT_STATUS_CHANGED", "PROJECT", id,
                Map.of("status", current.status()), Map.of("status", next), "API", metadata.ipHash());
        return projects.findById(principal.organizationId(), id).orElseThrow();
    }

    @Override
    public void beginOnboarding(UUID organizationId, UUID projectId, long version, UUID actorId, Instant now) {
        ProjectRecord current = requireProject(organizationId, projectId);
        if (current.status() != ProjectRecord.Status.DRAFT) {
            throw new DomainException("PROJECT_NOT_DRAFT", "Only a draft project can start onboarding.",
                    HttpStatus.CONFLICT);
        }
        if (!projects.transition(organizationId, projectId, current.status(), ProjectRecord.Status.ONBOARDING,
                current.status(), version, actorId, now)) throw conflict();
        activity(organizationId, projectId, actorId, "PROJECT_ONBOARDING_STARTED", projectId,
                "Project entered ONBOARDING.", now);
    }

    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public List<ProjectMember> members(TenantPrincipal principal, UUID projectId) {
        requireProject(principal.organizationId(), projectId);
        return projects.findMembers(principal.organizationId(), projectId);
    }

    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    @Transactional
    public ProjectMember addMember(TenantPrincipal principal, UUID projectId, MemberCommand command,
                                   RequestMetadata metadata) {
        requireProject(principal.organizationId(), projectId);
        var membership = organizations.findMember(principal.organizationId(), command.membershipId())
                .filter(value -> value.status()
                        == com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess.MembershipStatus.ACTIVE)
                .orElseThrow(this::notFound);
        Instant now = clock.instant();
        ProjectMember member = new ProjectMember(UUID.randomUUID(), principal.organizationId(), projectId,
                membership.id(), membership.userId(), membership.displayName(), membership.email(),
                required(command.assignmentRole(), 80), now);
        projects.insertMember(member, principal.userId());
        activity(principal.organizationId(), projectId, principal.userId(), "PROJECT_MEMBER_ADDED", member.id(),
                "Added " + member.displayName() + " as " + member.assignmentRole() + ".", now);
        audit.append(principal.organizationId(), principal.userId(), "PROJECT_MEMBER_ADDED", "PROJECT_MEMBER",
                member.id(), Map.of(), Map.of("projectId", projectId, "membershipId", member.membershipId()),
                "API", metadata.ipHash());
        return projects.findMembers(principal.organizationId(), projectId).stream()
                .filter(value -> value.id().equals(member.id())).findFirst().orElseThrow();
    }

    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    @Transactional
    public void removeMember(TenantPrincipal principal, UUID projectId, UUID memberId,
                             RequestMetadata metadata) {
        requireProject(principal.organizationId(), projectId);
        if (!projects.removeMember(principal.organizationId(), projectId, memberId)) throw notFound();
        activity(principal.organizationId(), projectId, principal.userId(), "PROJECT_MEMBER_REMOVED", memberId,
                "Project member removed.", clock.instant());
        audit.append(principal.organizationId(), principal.userId(), "PROJECT_MEMBER_REMOVED", "PROJECT_MEMBER",
                memberId, Map.of("projectId", projectId), Map.of("removed", true), "API", metadata.ipHash());
    }

    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public List<ActivityEntry> activity(TenantPrincipal principal, UUID projectId, int limit) {
        requireProject(principal.organizationId(), projectId);
        if (limit < 1 || limit > 100) throw validation("Limit must be 1 to 100.");
        return projects.findActivity(principal.organizationId(), projectId, limit);
    }

    private void activity(UUID organizationId, UUID projectId, UUID actorId, String action, UUID entityId,
                          String details, Instant now) {
        projects.appendActivity(new ActivityEntry(UUID.randomUUID(), organizationId, projectId, actorId, action,
                "PROJECT", entityId, details, now));
    }

    private ProjectRecord.Status nextStatus(ProjectRecord current, String actionValue) {
        String action = actionValue == null ? "" : actionValue.trim().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "HOLD" -> {
                if (!EnumSet.of(ProjectRecord.Status.ONBOARDING, ProjectRecord.Status.READY,
                        ProjectRecord.Status.ACTIVE).contains(current.status())) throw invalidTransition();
                yield ProjectRecord.Status.ON_HOLD;
            }
            case "RESUME" -> {
                if (current.status() != ProjectRecord.Status.ON_HOLD || current.previousStatus() == null
                        || !EnumSet.of(ProjectRecord.Status.ONBOARDING, ProjectRecord.Status.READY,
                        ProjectRecord.Status.ACTIVE).contains(current.previousStatus())) throw invalidTransition();
                yield current.previousStatus();
            }
            case "COMPLETE" -> {
                if (current.status() != ProjectRecord.Status.ACTIVE) throw invalidTransition();
                yield ProjectRecord.Status.COMPLETED;
            }
            case "CANCEL" -> {
                if (EnumSet.of(ProjectRecord.Status.CANCELLED, ProjectRecord.Status.COMPLETED,
                        ProjectRecord.Status.ARCHIVED).contains(current.status())) throw invalidTransition();
                yield ProjectRecord.Status.CANCELLED;
            }
            case "ARCHIVE" -> {
                if (!EnumSet.of(ProjectRecord.Status.COMPLETED, ProjectRecord.Status.CANCELLED)
                        .contains(current.status())) throw invalidTransition();
                yield ProjectRecord.Status.ARCHIVED;
            }
            default -> throw invalidTransition();
        };
    }

    private String optionalStatus(String value) { if (value == null || value.isBlank()) return null; try { return ProjectRecord.Status.valueOf(value.toUpperCase(Locale.ROOT)).name(); } catch (RuntimeException exception) { throw validation("Unknown project status."); } }
    private String required(String value, int max) { String clean = value == null ? "" : value.trim(); if (clean.isEmpty() || clean.length() > max) throw validation("A required value is invalid."); return clean; }
    private String optional(String value, int max) { if (value == null || value.isBlank()) return null; String clean = value.trim(); if (clean.length() > max) throw validation("A value is too long."); return clean; }
    private Long nonNegative(Long value) { if (value != null && value < 0) throw validation("Project value cannot be negative."); return value; }
    private String currency(String value) { if (value == null || value.isBlank()) return null; String clean = value.trim().toUpperCase(Locale.ROOT); if (!clean.matches("[A-Z]{3}")) throw validation("Currency must be a three-letter code."); return clean; }
    private int validPage(int value) { if (value < 0) throw validation("Page must be non-negative."); return value; }
    private int validSize(int value) { if (value < 1 || value > 100) throw validation("Size must be 1 to 100."); return value; }
    private DomainException validation(String message) { return new DomainException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST); }
    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "The resource changed. Refresh and try again.", HttpStatus.CONFLICT); }
    private DomainException invalidTransition() { return new DomainException("INVALID_PROJECT_TRANSITION", "The requested project transition is not allowed from its current state.", HttpStatus.CONFLICT); }

    public record ProjectCommand(UUID clientId, UUID serviceId, String name, String description,
                                 Long valueMinor, String currencyCode, LocalDate targetStartDate, long version) { }
    public record MemberCommand(UUID membershipId, String assignmentRole) { }
}
