package com.brainserve.onboarding.servicecatalog.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.servicecatalog.domain.model.ServiceDefinition;
import com.brainserve.onboarding.servicecatalog.domain.model.ServiceStatus;
import com.brainserve.onboarding.servicecatalog.infrastructure.persistence.ServiceDefinitionRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public read boundary used by projects without exposing service-catalog persistence. */
@Service
public class ServiceCatalogLookupService {
    private final ServiceDefinitionRepository services;

    public ServiceCatalogLookupService(ServiceDefinitionRepository services) { this.services = services; }

    @Transactional(readOnly = true)
    public ServiceRef requireActive(UUID organizationId, UUID serviceId) {
        ServiceDefinition service = services.findByOrganizationIdAndId(organizationId, serviceId)
                .orElseThrow(ServiceCatalogLookupService::notFound);
        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_ARCHIVED", "Archived services cannot be assigned to new projects.");
        }
        return new ServiceRef(service.getId(), service.getCode(), service.getName());
    }


    /**
     * Relationship-write guard. The shared database lock prevents service archival from racing a
     * project relationship write in the surrounding transaction.
     */
    @Transactional
    public ServiceRef requireActiveForRelationshipWrite(UUID organizationId, UUID serviceId) {
        ServiceDefinition service = services.findForRelationshipValidation(organizationId, serviceId)
                .orElseThrow(ServiceCatalogLookupService::notFound);
        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_ARCHIVED", "Archived services cannot be assigned to new projects.");
        }
        return new ServiceRef(service.getId(), service.getCode(), service.getName());
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> names(UUID organizationId, Collection<UUID> serviceIds) {
        Map<UUID, String> result = new LinkedHashMap<>();
        if (serviceIds.isEmpty()) return result;
        services.findAllByOrganizationIdAndIdIn(organizationId, serviceIds)
                .forEach(service -> result.put(service.getId(), service.getName()));
        return result;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource was not found.");
    }

    public record ServiceRef(UUID id, String code, String name) {}
}
