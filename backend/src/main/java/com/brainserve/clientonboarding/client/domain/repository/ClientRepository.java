package com.brainserve.clientonboarding.client.domain.repository;

import com.brainserve.clientonboarding.client.domain.model.ClientContact;
import com.brainserve.clientonboarding.client.domain.model.ClientRecord;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {
    PageSlice<ClientRecord> findPage(UUID organizationId, String search, String status, int page, int size);
    Optional<ClientRecord> findById(UUID organizationId, UUID clientId);
    ClientRecord insert(ClientRecord client, UUID actorId);
    boolean update(UUID organizationId, UUID clientId, ClientRecord replacement, long version,
                   UUID actorId, Instant now);
    boolean archive(UUID organizationId, UUID clientId, long version, UUID actorId, Instant now);
    List<ClientContact> findContacts(UUID organizationId, UUID clientId);
    Optional<ClientContact> findContact(UUID organizationId, UUID contactId);
    ClientContact insertContact(ClientContact contact, UUID actorId);
    boolean updateContact(UUID organizationId, UUID contactId, ClientContact replacement, long version,
                          UUID actorId, Instant now);
    boolean archiveContact(UUID organizationId, UUID contactId, long version, UUID actorId, Instant now);
    void assignPrimaryContact(UUID organizationId, UUID clientId, UUID contactId, UUID actorId, Instant now);
    void clearPrimaryContact(UUID organizationId, UUID clientId, UUID contactId);
}
