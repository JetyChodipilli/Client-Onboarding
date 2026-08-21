package com.brainserve.onboarding.client.application.service;

import com.brainserve.onboarding.client.domain.model.ClientContact;
import com.brainserve.onboarding.client.infrastructure.persistence.ClientContactRepository;
import com.brainserve.onboarding.common.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only contact boundary for invitation orchestration. */
@Service
public class ClientContactLookupService {
    private final ClientContactRepository contacts;

    public ClientContactLookupService(ClientContactRepository contacts) { this.contacts = contacts; }

    @Transactional(readOnly = true)
    public ContactRef requireForClient(UUID organizationId, UUID clientId, UUID contactId) {
        ClientContact contact = contacts.findByOrganizationIdAndId(organizationId, contactId)
                .filter(value -> value.getClientId().equals(clientId))
                .orElseThrow(ClientContactLookupService::notFound);
        return new ContactRef(contact.getId(), contact.getClientId(), contact.getDisplayName(), contact.getEmail(), contact.getNormalizedEmail());
    }

    @Transactional(readOnly = true)
    public List<ContactRef> listForClient(UUID organizationId, UUID clientId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return contacts.findAllByOrganizationIdAndClientId(organizationId, clientId,
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("id"))))
                .stream().map(contact -> new ContactRef(contact.getId(), contact.getClientId(),
                        contact.getDisplayName(), contact.getEmail(), contact.getNormalizedEmail())).toList();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested client contact was not found.");
    }

    public record ContactRef(UUID id, UUID clientId, String displayName, String email, String normalizedEmail) {}
}
