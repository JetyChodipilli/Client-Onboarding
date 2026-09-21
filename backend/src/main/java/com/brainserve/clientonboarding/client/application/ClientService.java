package com.brainserve.clientonboarding.client.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.client.domain.model.ClientContact;
import com.brainserve.clientonboarding.client.domain.model.ClientRecord;
import com.brainserve.clientonboarding.client.domain.repository.ClientRepository;
import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {
    private final ClientRepository clients;
    private final AuditService audit;
    private final Clock clock;

    public ClientService(ClientRepository clients, AuditService audit, Clock clock) {
        this.clients = clients;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public PageSlice<ClientRecord> list(TenantPrincipal principal, String search, String status, int page, int size) {
        return clients.findPage(principal.organizationId(), cleanSearch(search), optionalClientStatus(status),
                validPage(page), validSize(size));
    }

    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public ClientRecord get(TenantPrincipal principal, UUID clientId) {
        return clients.findById(principal.organizationId(), clientId).orElseThrow(this::notFound);
    }

    @PreAuthorize("hasAuthority('CLIENT_CREATE')")
    @Transactional
    public ClientRecord create(TenantPrincipal principal, ClientCommand command, RequestMetadata metadata) {
        Instant now = clock.instant();
        ClientRecord client = new ClientRecord(UUID.randomUUID(), principal.organizationId(),
                required(command.name(), 160), optional(command.legalName(), 200),
                parseMutableStatus(command.status()), optionalUrl(command.website()), optionalEmail(command.email()),
                optional(command.phone(), 50), optional(command.notes(), 2000), null, now, now, 0);
        clients.insert(client, principal.userId());
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_CREATED", "CLIENT", client.id(),
                Map.of(), Map.of("name", client.name(), "status", client.status()), "API", metadata.ipHash());
        return client;
    }

    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    @Transactional
    public ClientRecord update(TenantPrincipal principal, UUID clientId, ClientCommand command,
                               RequestMetadata metadata) {
        ClientRecord current = getForMutation(principal, clientId);
        ClientRecord replacement = new ClientRecord(current.id(), current.organizationId(),
                required(command.name(), 160), optional(command.legalName(), 200),
                parseMutableStatus(command.status()), optionalUrl(command.website()), optionalEmail(command.email()),
                optional(command.phone(), 50), optional(command.notes(), 2000), current.archivedAt(),
                current.createdAt(), clock.instant(), current.version());
        if (!clients.update(principal.organizationId(), clientId, replacement, command.version(), principal.userId(),
                clock.instant())) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_UPDATED", "CLIENT", clientId,
                Map.of("name", current.name(), "status", current.status()),
                Map.of("name", replacement.name(), "status", replacement.status()), "API", metadata.ipHash());
        return clients.findById(principal.organizationId(), clientId).orElseThrow();
    }

    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    @Transactional
    public void archive(TenantPrincipal principal, UUID clientId, long version, RequestMetadata metadata) {
        ClientRecord current = getForMutation(principal, clientId);
        if (!clients.archive(principal.organizationId(), clientId, version, principal.userId(), clock.instant())) {
            throw conflict();
        }
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_ARCHIVED", "CLIENT", clientId,
                Map.of("status", current.status()), Map.of("status", "ARCHIVED"), "API", metadata.ipHash());
    }

    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public List<ClientContact> contacts(TenantPrincipal principal, UUID clientId) {
        get(principal, clientId);
        return clients.findContacts(principal.organizationId(), clientId);
    }

    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    @Transactional
    public ClientContact createContact(TenantPrincipal principal, UUID clientId, ContactCommand command,
                                       RequestMetadata metadata) {
        getForMutation(principal, clientId);
        Instant now = clock.instant();
        ClientContact contact = new ClientContact(UUID.randomUUID(), principal.organizationId(), clientId,
                required(command.name(), 160), requiredEmail(command.email()), optional(command.phone(), 50),
                optional(command.jobTitle(), 120), command.primary(), null, now, now, 0);
        clients.insertContact(contact, principal.userId());
        if (command.primary()) clients.assignPrimaryContact(principal.organizationId(), clientId, contact.id(),
                principal.userId(), now);
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_CONTACT_CREATED", "CLIENT_CONTACT",
                contact.id(), Map.of(), Map.of("clientId", clientId, "primary", command.primary()),
                "API", metadata.ipHash());
        return clients.findContact(principal.organizationId(), contact.id()).orElseThrow();
    }

    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    @Transactional
    public ClientContact updateContact(TenantPrincipal principal, UUID contactId, ContactCommand command,
                                       RequestMetadata metadata) {
        ClientContact current = clients.findContact(principal.organizationId(), contactId).orElseThrow(this::notFound);
        if (current.archivedAt() != null) throw notFound();
        ClientContact replacement = new ClientContact(current.id(), current.organizationId(), current.clientId(),
                required(command.name(), 160), requiredEmail(command.email()), optional(command.phone(), 50),
                optional(command.jobTitle(), 120), command.primary(), null, current.createdAt(), clock.instant(),
                current.version());
        if (!clients.updateContact(principal.organizationId(), contactId, replacement, command.version(),
                principal.userId(), clock.instant())) throw conflict();
        if (command.primary()) clients.assignPrimaryContact(principal.organizationId(), current.clientId(), contactId,
                principal.userId(), clock.instant());
        else if (current.primary()) clients.clearPrimaryContact(principal.organizationId(), current.clientId(),
                contactId);
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_CONTACT_UPDATED", "CLIENT_CONTACT",
                contactId, Map.of("primary", current.primary()), Map.of("primary", command.primary()),
                "API", metadata.ipHash());
        return clients.findContact(principal.organizationId(), contactId).orElseThrow();
    }

    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    @Transactional
    public void archiveContact(TenantPrincipal principal, UUID contactId, long version,
                               RequestMetadata metadata) {
        ClientContact current = clients.findContact(principal.organizationId(), contactId).orElseThrow(this::notFound);
        if (!clients.archiveContact(principal.organizationId(), contactId, version, principal.userId(),
                clock.instant())) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_CONTACT_ARCHIVED", "CLIENT_CONTACT",
                contactId, Map.of("clientId", current.clientId()), Map.of("archived", true),
                "API", metadata.ipHash());
    }

    private ClientRecord getForMutation(TenantPrincipal principal, UUID id) {
        ClientRecord client = clients.findById(principal.organizationId(), id).orElseThrow(this::notFound);
        if (client.archivedAt() != null) throw notFound();
        return client;
    }

    private ClientRecord.Status parseMutableStatus(String value) {
        try {
            ClientRecord.Status status = ClientRecord.Status.valueOf(value.toUpperCase(Locale.ROOT));
            if (status == ClientRecord.Status.ARCHIVED) throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException exception) {
            throw validation("Client status must be PROSPECT, ACTIVE, or INACTIVE.");
        }
    }

    private String optionalClientStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ClientRecord.Status.valueOf(value.toUpperCase(Locale.ROOT)).name(); }
        catch (RuntimeException exception) { throw validation("Unknown client status."); }
    }

    private String required(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > max) throw validation("A required value is invalid.");
        return clean;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > max) throw validation("A value is too long.");
        return clean;
    }

    private String optionalEmail(String value) {
        if (value == null || value.isBlank()) return null;
        return requiredEmail(value);
    }

    private String requiredEmail(String value) {
        String clean = required(value, 254).toLowerCase(Locale.ROOT);
        if (!clean.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw validation("A valid email is required.");
        return clean;
    }

    private String optionalUrl(String value) {
        String clean = optional(value, 500);
        if (clean != null && !clean.matches("https?://.+")) throw validation("Website must use HTTP or HTTPS.");
        return clean;
    }

    private String cleanSearch(String value) { return value == null ? "" : value.trim(); }
    private int validPage(int value) { if (value < 0) throw validation("Page must be non-negative."); return value; }
    private int validSize(int value) { if (value < 1 || value > 100) throw validation("Size must be 1 to 100."); return value; }
    private DomainException validation(String message) { return new DomainException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST); }
    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "The resource changed. Refresh and try again.", HttpStatus.CONFLICT); }

    public record ClientCommand(String name, String legalName, String status, String website, String email,
                                String phone, String notes, long version) { }
    public record ContactCommand(String name, String email, String phone, String jobTitle, boolean primary,
                                 long version) { }
}
