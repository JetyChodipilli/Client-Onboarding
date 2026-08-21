package com.brainserve.onboarding.client.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.api.request.ArchiveClientRequest;
import com.brainserve.onboarding.client.api.request.CreateClientContactRequest;
import com.brainserve.onboarding.client.api.request.CreateClientRequest;
import com.brainserve.onboarding.client.api.request.UpdateClientContactRequest;
import com.brainserve.onboarding.client.api.request.UpdateClientRequest;
import com.brainserve.onboarding.client.api.response.ClientContactResponse;
import com.brainserve.onboarding.client.api.response.ClientResponse;
import com.brainserve.onboarding.client.domain.model.Client;
import com.brainserve.onboarding.client.domain.model.ClientContact;
import com.brainserve.onboarding.client.domain.model.ClientStatus;
import com.brainserve.onboarding.client.infrastructure.persistence.ClientContactRepository;
import com.brainserve.onboarding.client.infrastructure.persistence.ClientRepository;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
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
public class ClientService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ClientRepository clients;
    private final ClientContactRepository contacts;
    private final AuditService audit;
    private final ActivityTimelineService activity;
    private final Clock clock;

    public ClientService(ClientRepository clients, ClientContactRepository contacts, AuditService audit,
                         ActivityTimelineService activity, Clock clock) {
        this.clients = clients;
        this.contacts = contacts;
        this.audit = audit;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public ClientResponse create(TenantPrincipal principal, CreateClientRequest request, HttpServletRequest servletRequest) {
        Instant now = clock.instant();
        Client client = clients.saveAndFlush(new Client(UUID.randomUUID(), principal.organizationId(), request.name(), principal.userId(), now));
        activity.record(principal.organizationId(), client.getId(), null, principal.userId(), "CLIENT_CREATED",
                "CLIENT", client.getId(), "Client created", Map.of("status", client.getStatus().name()));
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_CREATED", "CLIENT", client.getId(), null,
                Map.of("name", client.getName(), "status", client.getStatus()), servletRequest);
        return map(client);
    }

    @Transactional(readOnly = true)
    public PageResult<ClientResponse> list(TenantPrincipal principal, int page, int size, ClientStatus status,
                                           String query, boolean includeArchived) {
        var pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        String searchPrefix = searchPrefix(query);
        Page<Client> result;
        if (searchPrefix != null && status != null) {
            result = clients.searchByStatus(principal.organizationId(), status, searchPrefix, pageable);
        } else if (searchPrefix != null && includeArchived) {
            result = clients.searchAll(principal.organizationId(), searchPrefix, pageable);
        } else if (searchPrefix != null) {
            result = clients.searchActive(principal.organizationId(), ClientStatus.ARCHIVED, searchPrefix, pageable);
        } else if (status != null) {
            result = clients.findAllByOrganizationIdAndStatus(principal.organizationId(), status, pageable);
        } else if (includeArchived) {
            result = clients.findAllByOrganizationId(principal.organizationId(), pageable);
        } else {
            result = clients.findAllByOrganizationIdAndStatusNot(principal.organizationId(), ClientStatus.ARCHIVED, pageable);
        }
        return new PageResult<>(result.map(ClientService::map).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ClientResponse get(TenantPrincipal principal, UUID clientId) {
        return map(requireClient(principal.organizationId(), clientId));
    }

    @Transactional
    public ClientResponse update(TenantPrincipal principal, UUID clientId, UpdateClientRequest request, HttpServletRequest servletRequest) {
        Client client = requireClient(principal.organizationId(), clientId);
        requireVersion(client.getVersion(), request.version());
        Map<String, Object> before = Map.of("name", client.getName(), "status", client.getStatus(), "version", client.getVersion());
        try {
            client.update(request.name(), request.status(), principal.userId(), clock.instant());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "CLIENT_STATE_INVALID", ex.getMessage());
        }
        clients.saveAndFlush(client);
        activity.record(principal.organizationId(), client.getId(), null, principal.userId(), "CLIENT_UPDATED",
                "CLIENT", client.getId(), "Client details updated", Map.of("status", client.getStatus().name()));
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_UPDATED", "CLIENT", client.getId(), before,
                Map.of("name", client.getName(), "status", client.getStatus(), "version", client.getVersion()), servletRequest);
        return map(client);
    }

    @Transactional
    public ClientResponse archive(TenantPrincipal principal, UUID clientId, ArchiveClientRequest request, HttpServletRequest servletRequest) {
        Client client = requireClient(principal.organizationId(), clientId);
        requireVersion(client.getVersion(), request.version());
        ClientStatus before = client.getStatus();
        client.archive(principal.userId(), clock.instant());
        clients.saveAndFlush(client);
        activity.record(principal.organizationId(), client.getId(), null, principal.userId(), "CLIENT_ARCHIVED",
                "CLIENT", client.getId(), "Client archived", Map.of("from", before.name(), "to", client.getStatus().name()));
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_ARCHIVED", "CLIENT", client.getId(),
                Map.of("status", before), Map.of("status", client.getStatus()), servletRequest);
        return map(client);
    }

    @Transactional
    public ClientContactResponse createContact(TenantPrincipal principal, UUID clientId, CreateClientContactRequest request,
                                               HttpServletRequest servletRequest) {
        Client client = requireClient(principal.organizationId(), clientId);
        if (client.getStatus() == ClientStatus.ARCHIVED) throw archivedClient();
        String normalizedEmail = ClientContact.normalizeEmail(request.email());
        if (contacts.existsByOrganizationIdAndClientIdAndNormalizedEmail(principal.organizationId(), clientId, normalizedEmail)) {
            throw duplicateContact();
        }
        Instant now = clock.instant();
        ClientContact contact = contacts.saveAndFlush(new ClientContact(UUID.randomUUID(), principal.organizationId(), clientId,
                request.displayName(), request.email(), request.jobTitle(), request.phone(), principal.userId(), now));
        activity.record(principal.organizationId(), clientId, null, principal.userId(), "CLIENT_CONTACT_CREATED",
                "CLIENT_CONTACT", contact.getId(), "Client contact added", Map.of("displayName", contact.getDisplayName()));
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_CONTACT_CREATED", "CLIENT_CONTACT", contact.getId(), null,
                Map.of("clientId", clientId, "email", contact.getNormalizedEmail()), servletRequest);
        return map(contact);
    }

    @Transactional(readOnly = true)
    public PageResult<ClientContactResponse> listContacts(TenantPrincipal principal, UUID clientId, int page, int size) {
        requireClient(principal.organizationId(), clientId);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.ASC, "displayName").and(Sort.by("id")));
        Page<ClientContact> result = contacts.findAllByOrganizationIdAndClientId(principal.organizationId(), clientId, pageable);
        return new PageResult<>(result.map(ClientService::map).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ClientContactResponse updateContact(TenantPrincipal principal, UUID contactId, UpdateClientContactRequest request,
                                               HttpServletRequest servletRequest) {
        ClientContact contact = contacts.findByOrganizationIdAndId(principal.organizationId(), contactId).orElseThrow(ClientService::notFound);
        Client client = requireClient(principal.organizationId(), contact.getClientId());
        if (client.getStatus() == ClientStatus.ARCHIVED) throw archivedClient();
        requireVersion(contact.getVersion(), request.version());
        String normalizedEmail = ClientContact.normalizeEmail(request.email());
        if (contacts.existsByOrganizationIdAndClientIdAndNormalizedEmailAndIdNot(
                principal.organizationId(), contact.getClientId(), normalizedEmail, contactId)) throw duplicateContact();
        Map<String, Object> before = Map.of("displayName", contact.getDisplayName(), "email", contact.getNormalizedEmail(), "version", contact.getVersion());
        contact.update(request.displayName(), request.email(), request.jobTitle(), request.phone(), principal.userId(), clock.instant());
        contacts.saveAndFlush(contact);
        activity.record(principal.organizationId(), contact.getClientId(), null, principal.userId(), "CLIENT_CONTACT_UPDATED",
                "CLIENT_CONTACT", contact.getId(), "Client contact updated", Map.of("displayName", contact.getDisplayName()));
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_CONTACT_UPDATED", "CLIENT_CONTACT", contact.getId(), before,
                Map.of("displayName", contact.getDisplayName(), "email", contact.getNormalizedEmail(), "version", contact.getVersion()), servletRequest);
        return map(contact);
    }

    @Transactional(readOnly = true)
    public ActivityTimelineService.PageResult activity(TenantPrincipal principal, UUID clientId, int page, int size) {
        requireClient(principal.organizationId(), clientId);
        return activity.listByClient(principal.organizationId(), clientId, page, size);
    }

    private Client requireClient(UUID organizationId, UUID clientId) {
        return clients.findByOrganizationIdAndId(organizationId, clientId).orElseThrow(ClientService::notFound);
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
    private static ApiException archivedClient() { return new ApiException(HttpStatus.CONFLICT, "CLIENT_ARCHIVED", "Archived clients cannot be modified."); }
    private static ApiException duplicateContact() { return new ApiException(HttpStatus.CONFLICT, "CLIENT_CONTACT_EXISTS", "A contact with this email already exists for the client."); }

    private static ClientResponse map(Client client) {
        return new ClientResponse(client.getId(), client.getName(), client.getStatus(), client.getArchivedAt(), client.getCreatedAt(), client.getUpdatedAt(), client.getVersion());
    }

    private static ClientContactResponse map(ClientContact contact) {
        return new ClientContactResponse(contact.getId(), contact.getClientId(), contact.getDisplayName(), contact.getEmail(),
                contact.getJobTitle(), contact.getPhone(), contact.getCreatedAt(), contact.getUpdatedAt(), contact.getVersion());
    }

    public record PageResult<T>(java.util.List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = java.util.List.copyOf(items); }
    }
}
