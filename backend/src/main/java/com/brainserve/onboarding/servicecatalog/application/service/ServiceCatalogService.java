package com.brainserve.onboarding.servicecatalog.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.servicecatalog.api.request.ArchiveServiceRequest;
import com.brainserve.onboarding.servicecatalog.api.request.CreateServiceRequest;
import com.brainserve.onboarding.servicecatalog.api.request.UpdateServiceRequest;
import com.brainserve.onboarding.servicecatalog.api.response.ServiceResponse;
import com.brainserve.onboarding.servicecatalog.domain.model.ServiceDefinition;
import com.brainserve.onboarding.servicecatalog.domain.model.ServiceStatus;
import com.brainserve.onboarding.servicecatalog.infrastructure.persistence.ServiceDefinitionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceCatalogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ServiceDefinitionRepository services;
    private final AuditService audit;
    private final Clock clock;

    public ServiceCatalogService(ServiceDefinitionRepository services, AuditService audit, Clock clock) {
        this.services = services;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ServiceResponse create(TenantPrincipal principal, CreateServiceRequest request, HttpServletRequest servletRequest) {
        String code = ServiceDefinition.normalizeCode(request.code());
        if (services.existsByOrganizationIdAndCode(principal.organizationId(), code)) throw duplicateCode();
        Instant now = clock.instant();
        ServiceDefinition service = services.saveAndFlush(new ServiceDefinition(UUID.randomUUID(), principal.organizationId(), code,
                request.name(), request.description(), principal.userId(), now));
        audit.record(principal.organizationId(), principal.userId(), "SERVICE_CREATED", "SERVICE", service.getId(), null,
                Map.of("code", service.getCode(), "name", service.getName()), servletRequest);
        return map(service);
    }

    @Transactional(readOnly = true)
    public PageResult<ServiceResponse> list(TenantPrincipal principal, int page, int size, String query, boolean includeArchived) {
        var pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id")));
        String searchPrefix = searchPrefix(query);
        Page<ServiceDefinition> result;
        if (searchPrefix != null && includeArchived) {
            result = services.searchAll(principal.organizationId(), searchPrefix, pageable);
        } else if (searchPrefix != null) {
            result = services.searchActive(principal.organizationId(), ServiceStatus.ACTIVE, searchPrefix, pageable);
        } else if (includeArchived) {
            result = services.findAllByOrganizationId(principal.organizationId(), pageable);
        } else {
            result = services.findAllByOrganizationIdAndStatus(principal.organizationId(), ServiceStatus.ACTIVE, pageable);
        }
        return new PageResult<>(result.map(ServiceCatalogService::map).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(TenantPrincipal principal, UUID serviceId) { return map(requireService(principal.organizationId(), serviceId)); }

    @Transactional
    public ServiceResponse update(TenantPrincipal principal, UUID serviceId, UpdateServiceRequest request, HttpServletRequest servletRequest) {
        ServiceDefinition service = requireService(principal.organizationId(), serviceId);
        requireVersion(service.getVersion(), request.version());
        Map<String, Object> before = Map.of("name", service.getName(), "description", service.getDescription() == null ? "" : service.getDescription(), "version", service.getVersion());
        try {
            service.update(request.name(), request.description(), principal.userId(), clock.instant());
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_ARCHIVED", ex.getMessage());
        }
        services.saveAndFlush(service);
        audit.record(principal.organizationId(), principal.userId(), "SERVICE_UPDATED", "SERVICE", service.getId(), before,
                Map.of("name", service.getName(), "version", service.getVersion()), servletRequest);
        return map(service);
    }

    @Transactional
    public ServiceResponse archive(TenantPrincipal principal, UUID serviceId, ArchiveServiceRequest request, HttpServletRequest servletRequest) {
        ServiceDefinition service = requireService(principal.organizationId(), serviceId);
        requireVersion(service.getVersion(), request.version());
        ServiceStatus before = service.getStatus();
        service.archive(principal.userId(), clock.instant());
        services.saveAndFlush(service);
        audit.record(principal.organizationId(), principal.userId(), "SERVICE_ARCHIVED", "SERVICE", service.getId(),
                Map.of("status", before), Map.of("status", service.getStatus()), servletRequest);
        return map(service);
    }

    private ServiceDefinition requireService(UUID organizationId, UUID serviceId) {
        return services.findByOrganizationIdAndId(organizationId, serviceId).orElseThrow(ServiceCatalogService::notFound);
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
    private static ApiException duplicateCode() { return new ApiException(HttpStatus.CONFLICT, "SERVICE_CODE_EXISTS", "A service with this code already exists."); }

    private static ServiceResponse map(ServiceDefinition service) {
        return new ServiceResponse(service.getId(), service.getCode(), service.getName(), service.getDescription(), service.getStatus(),
                service.getArchivedAt(), service.getCreatedAt(), service.getUpdatedAt(), service.getVersion());
    }

    public record PageResult<T>(java.util.List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = java.util.List.copyOf(items); }
    }
}
