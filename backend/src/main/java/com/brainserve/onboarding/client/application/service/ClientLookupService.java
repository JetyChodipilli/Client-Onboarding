package com.brainserve.onboarding.client.application.service;

import com.brainserve.onboarding.client.domain.model.Client;
import com.brainserve.onboarding.client.domain.model.ClientStatus;
import com.brainserve.onboarding.client.infrastructure.persistence.ClientRepository;
import com.brainserve.onboarding.common.error.ApiException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public read boundary used by sibling modules without exposing client persistence. */
@Service
public class ClientLookupService {
    private final ClientRepository clients;

    public ClientLookupService(ClientRepository clients) {
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public ClientRef requireUsable(UUID organizationId, UUID clientId) {
        Client client = clients.findByOrganizationIdAndId(organizationId, clientId).orElseThrow(ClientLookupService::notFound);
        if (client.getStatus() == ClientStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "CLIENT_ARCHIVED", "Archived clients cannot receive new projects.");
        }
        return new ClientRef(client.getId(), client.getName(), client.getStatus());
    }


    /**
     * Relationship-write guard. The shared database lock prevents an archive from committing between
     * validation and the caller's relationship insert/update transaction. Callers must use this inside
     * their existing write transaction.
     */
    @Transactional
    public ClientRef requireUsableForRelationshipWrite(UUID organizationId, UUID clientId) {
        Client client = clients.findForRelationshipValidation(organizationId, clientId)
                .orElseThrow(ClientLookupService::notFound);
        if (client.getStatus() == ClientStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "CLIENT_ARCHIVED", "Archived clients cannot receive new projects.");
        }
        return new ClientRef(client.getId(), client.getName(), client.getStatus());
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> names(UUID organizationId, Collection<UUID> clientIds) {
        Map<UUID, String> result = new LinkedHashMap<>();
        if (clientIds.isEmpty()) return result;
        clients.findAllByOrganizationIdAndIdIn(organizationId, clientIds)
                .forEach(client -> result.put(client.getId(), client.getName()));
        return result;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource was not found.");
    }

    public record ClientRef(UUID id, String name, ClientStatus status) {}
}
