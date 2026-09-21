package com.brainserve.clientonboarding.servicecatalog.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.servicecatalog.domain.model.ServiceDefinition;
import com.brainserve.clientonboarding.servicecatalog.domain.repository.ServiceCatalogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceCatalogService {
    private final ServiceCatalogRepository services;
    private final AuditService audit;
    private final Clock clock;

    public ServiceCatalogService(ServiceCatalogRepository services, AuditService audit, Clock clock) {
        this.services = services; this.audit = audit; this.clock = clock;
    }

    @PreAuthorize("hasAnyAuthority('SERVICE_MANAGE','PROJECT_CREATE','WORKFLOW_MANAGE')")
    public PageSlice<ServiceDefinition> list(TenantPrincipal principal, String search, String status,
                                              int page, int size) {
        return services.findPage(principal.organizationId(), search == null ? "" : search.trim(),
                optionalStatus(status), validPage(page), validSize(size));
    }

    @PreAuthorize("hasAnyAuthority('SERVICE_MANAGE','PROJECT_CREATE','PROJECT_UPDATE','WORKFLOW_MANAGE')")
    public ServiceDefinition get(TenantPrincipal principal, UUID serviceId) {
        return services.findById(principal.organizationId(), serviceId).orElseThrow(this::notFound);
    }

    @PreAuthorize("hasAuthority('SERVICE_MANAGE')")
    @Transactional
    public ServiceDefinition create(TenantPrincipal principal, ServiceCommand command, RequestMetadata metadata) {
        Instant now = clock.instant();
        ServiceDefinition service = new ServiceDefinition(UUID.randomUUID(), principal.organizationId(),
                code(command.code()), required(command.name(), 160), optional(command.description(), 1000),
                parseMutableStatus(command.status()), null, now, now, 0);
        services.insert(service, principal.userId());
        audit.append(principal.organizationId(), principal.userId(), "SERVICE_CREATED", "SERVICE", service.id(),
                Map.of(), Map.of("code", service.code(), "name", service.name()), "API", metadata.ipHash());
        return service;
    }

    @PreAuthorize("hasAuthority('SERVICE_MANAGE')")
    @Transactional
    public ServiceDefinition update(TenantPrincipal principal, UUID id, ServiceCommand command,
                                    RequestMetadata metadata) {
        ServiceDefinition current = getForMutation(principal, id);
        ServiceDefinition replacement = new ServiceDefinition(id, principal.organizationId(), code(command.code()),
                required(command.name(), 160), optional(command.description(), 1000),
                parseMutableStatus(command.status()), null, current.createdAt(), clock.instant(), current.version());
        if (!services.update(principal.organizationId(), id, replacement, command.version(), principal.userId(),
                clock.instant())) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "SERVICE_UPDATED", "SERVICE", id,
                Map.of("code", current.code(), "status", current.status()),
                Map.of("code", replacement.code(), "status", replacement.status()), "API", metadata.ipHash());
        return services.findById(principal.organizationId(), id).orElseThrow();
    }

    @PreAuthorize("hasAuthority('SERVICE_MANAGE')")
    @Transactional
    public void archive(TenantPrincipal principal, UUID id, long version, RequestMetadata metadata) {
        ServiceDefinition current = getForMutation(principal, id);
        if (!services.archive(principal.organizationId(), id, version, principal.userId(), clock.instant())) {
            throw new DomainException("SERVICE_IN_USE_OR_STALE",
                    "The service is used by an open project or changed by another request.", HttpStatus.CONFLICT);
        }
        audit.append(principal.organizationId(), principal.userId(), "SERVICE_ARCHIVED", "SERVICE", id,
                Map.of("status", current.status()), Map.of("status", "ARCHIVED"), "API", metadata.ipHash());
    }

    private ServiceDefinition getForMutation(TenantPrincipal principal, UUID id) {
        ServiceDefinition value = services.findById(principal.organizationId(), id).orElseThrow(this::notFound);
        if (value.archivedAt() != null) throw notFound();
        return value;
    }

    private ServiceDefinition.Status parseMutableStatus(String value) {
        try {
            ServiceDefinition.Status status = ServiceDefinition.Status.valueOf(value.toUpperCase(Locale.ROOT));
            if (status == ServiceDefinition.Status.ARCHIVED) throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException exception) { throw validation("Service status must be ACTIVE or INACTIVE."); }
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ServiceDefinition.Status.valueOf(value.toUpperCase(Locale.ROOT)).name(); }
        catch (RuntimeException exception) { throw validation("Unknown service status."); }
    }

    private String code(String value) {
        String clean = required(value, 80).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        if (!clean.matches("[A-Z0-9][A-Z0-9_-]*")) throw validation("A valid service code is required.");
        return clean;
    }

    private String required(String value, int max) { String clean = value == null ? "" : value.trim(); if (clean.isEmpty() || clean.length() > max) throw validation("A required value is invalid."); return clean; }
    private String optional(String value, int max) { if (value == null || value.isBlank()) return null; String clean = value.trim(); if (clean.length() > max) throw validation("A value is too long."); return clean; }
    private int validPage(int value) { if (value < 0) throw validation("Page must be non-negative."); return value; }
    private int validSize(int value) { if (value < 1 || value > 100) throw validation("Size must be 1 to 100."); return value; }
    private DomainException validation(String message) { return new DomainException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST); }
    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "The resource changed. Refresh and try again.", HttpStatus.CONFLICT); }

    public record ServiceCommand(String code, String name, String description, String status, long version) { }
}
