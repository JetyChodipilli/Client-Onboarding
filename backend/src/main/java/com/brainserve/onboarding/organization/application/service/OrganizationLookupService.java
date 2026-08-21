package com.brainserve.onboarding.organization.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Narrow read-only organization boundary for sibling modules that need display identity only. */
@Service
public class OrganizationLookupService {
    private final OrganizationRepository organizations;

    public OrganizationLookupService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public OrganizationRef require(UUID organizationId) {
        var organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested organization was not found."));
        return new OrganizationRef(organization.getId(), organization.getName(), organization.getSlug());
    }

    public record OrganizationRef(UUID id, String name, String slug) {}
}
