package com.brainserve.clientonboarding.servicecatalog.domain.repository;

import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.servicecatalog.domain.model.ServiceDefinition;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ServiceCatalogRepository {
    PageSlice<ServiceDefinition> findPage(UUID organizationId, String search, String status, int page, int size);
    Optional<ServiceDefinition> findById(UUID organizationId, UUID serviceId);
    ServiceDefinition insert(ServiceDefinition service, UUID actorId);
    boolean update(UUID organizationId, UUID serviceId, ServiceDefinition replacement, long version,
                   UUID actorId, Instant now);
    boolean archive(UUID organizationId, UUID serviceId, long version, UUID actorId, Instant now);
}
