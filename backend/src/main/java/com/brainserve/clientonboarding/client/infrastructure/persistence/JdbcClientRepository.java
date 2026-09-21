package com.brainserve.clientonboarding.client.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.client.domain.model.ClientContact;
import com.brainserve.clientonboarding.client.domain.model.ClientRecord;
import com.brainserve.clientonboarding.client.domain.repository.ClientRepository;
import com.brainserve.clientonboarding.common.api.PageSlice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcClientRepository implements ClientRepository {
    private final JdbcClient jdbc;

    public JdbcClientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageSlice<ClientRecord> findPage(UUID organizationId, String search, String status, int page, int size) {
        String where = " WHERE organization_id = :organizationId AND (LOWER(name) LIKE :search OR LOWER(COALESCE(legal_name, '')) LIKE :search)";
        if (status != null) where += " AND status = :status";
        var itemsQuery = jdbc.sql("SELECT * FROM clients" + where
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("organizationId", organizationId).param("search", "%" + search.toLowerCase() + "%")
                .param("limit", size).param("offset", page * size);
        var countQuery = jdbc.sql("SELECT COUNT(*) FROM clients" + where)
                .param("organizationId", organizationId).param("search", "%" + search.toLowerCase() + "%");
        if (status != null) {
            itemsQuery = itemsQuery.param("status", status);
            countQuery = countQuery.param("status", status);
        }
        return new PageSlice<>(itemsQuery.query(this::mapClient).list(), page, size,
                countQuery.query(Long.class).single());
    }

    @Override
    public Optional<ClientRecord> findById(UUID organizationId, UUID clientId) {
        return jdbc.sql("SELECT * FROM clients WHERE organization_id = :organizationId AND id = :id")
                .param("organizationId", organizationId).param("id", clientId)
                .query(this::mapClient).optional();
    }

    @Override
    public ClientRecord insert(ClientRecord client, UUID actorId) {
        jdbc.sql("""
                INSERT INTO clients (id, organization_id, name, legal_name, status, website, email, phone, notes,
                    archived_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :name, :legalName, :status, :website, :email, :phone, :notes,
                    NULL, :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", client.id()).param("organizationId", client.organizationId())
                .param("name", client.name()).param("legalName", client.legalName())
                .param("status", client.status().name()).param("website", client.website())
                .param("email", client.email()).param("phone", client.phone()).param("notes", client.notes())
                .param("createdAt", timestamp(client.createdAt())).param("updatedAt", timestamp(client.updatedAt()))
                .param("actor", actorId).update();
        return client;
    }

    @Override
    public boolean update(UUID organizationId, UUID clientId, ClientRecord replacement, long version,
                          UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE clients SET name = :name, legal_name = :legalName, status = :status,
                    website = :website, email = :email, phone = :phone, notes = :notes,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL
                """).param("name", replacement.name()).param("legalName", replacement.legalName())
                .param("status", replacement.status().name()).param("website", replacement.website())
                .param("email", replacement.email()).param("phone", replacement.phone())
                .param("notes", replacement.notes()).param("now", timestamp(now)).param("actor", actorId)
                .param("organizationId", organizationId).param("id", clientId).param("version", version)
                .update() == 1;
    }

    @Override
    public boolean archive(UUID organizationId, UUID clientId, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE clients SET status = 'ARCHIVED', archived_at = :now, updated_at = :now,
                    updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL
                """).param("now", timestamp(now)).param("actor", actorId)
                .param("organizationId", organizationId).param("id", clientId).param("version", version)
                .update() == 1;
    }

    @Override
    public List<ClientContact> findContacts(UUID organizationId, UUID clientId) {
        return jdbc.sql("""
                SELECT cc.*, CASE WHEN pc.contact_id IS NULL THEN FALSE ELSE TRUE END AS is_primary
                FROM client_contacts cc
                LEFT JOIN client_primary_contacts pc ON pc.organization_id = cc.organization_id
                    AND pc.client_id = cc.client_id AND pc.contact_id = cc.id
                WHERE cc.organization_id = :organizationId AND cc.client_id = :clientId
                ORDER BY is_primary DESC, cc.name, cc.id
                """).param("organizationId", organizationId).param("clientId", clientId)
                .query(this::mapContact).list();
    }

    @Override
    public Optional<ClientContact> findContact(UUID organizationId, UUID contactId) {
        return jdbc.sql("""
                SELECT cc.*, CASE WHEN pc.contact_id IS NULL THEN FALSE ELSE TRUE END AS is_primary
                FROM client_contacts cc
                LEFT JOIN client_primary_contacts pc ON pc.organization_id = cc.organization_id
                    AND pc.client_id = cc.client_id AND pc.contact_id = cc.id
                WHERE cc.organization_id = :organizationId AND cc.id = :id
                """).param("organizationId", organizationId).param("id", contactId)
                .query(this::mapContact).optional();
    }

    @Override
    public ClientContact insertContact(ClientContact contact, UUID actorId) {
        jdbc.sql("""
                INSERT INTO client_contacts (id, organization_id, client_id, name, email, phone, job_title,
                    archived_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :clientId, :name, :email, :phone, :jobTitle,
                    NULL, :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", contact.id()).param("organizationId", contact.organizationId())
                .param("clientId", contact.clientId()).param("name", contact.name()).param("email", contact.email())
                .param("phone", contact.phone()).param("jobTitle", contact.jobTitle())
                .param("createdAt", timestamp(contact.createdAt())).param("updatedAt", timestamp(contact.updatedAt()))
                .param("actor", actorId).update();
        return contact;
    }

    @Override
    public boolean updateContact(UUID organizationId, UUID contactId, ClientContact replacement, long version,
                                 UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE client_contacts SET name = :name, email = :email, phone = :phone,
                    job_title = :jobTitle, updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL
                """).param("name", replacement.name()).param("email", replacement.email())
                .param("phone", replacement.phone()).param("jobTitle", replacement.jobTitle())
                .param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", contactId).param("version", version).update() == 1;
    }

    @Override
    public boolean archiveContact(UUID organizationId, UUID contactId, long version, UUID actorId, Instant now) {
        jdbc.sql("DELETE FROM client_primary_contacts WHERE organization_id = :organizationId AND contact_id = :id")
                .param("organizationId", organizationId).param("id", contactId).update();
        return jdbc.sql("""
                UPDATE client_contacts SET archived_at = :now, updated_at = :now, updated_by = :actor,
                    version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL
                """).param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", contactId).param("version", version).update() == 1;
    }

    @Override
    public void assignPrimaryContact(UUID organizationId, UUID clientId, UUID contactId, UUID actorId, Instant now) {
        jdbc.sql("SELECT id FROM clients WHERE organization_id = :organizationId AND id = :clientId FOR UPDATE")
                .param("organizationId", organizationId).param("clientId", clientId).query(UUID.class).single();
        jdbc.sql("DELETE FROM client_primary_contacts WHERE organization_id = :organizationId AND client_id = :clientId")
                .param("organizationId", organizationId).param("clientId", clientId).update();
        jdbc.sql("""
                INSERT INTO client_primary_contacts (organization_id, client_id, contact_id, assigned_at, assigned_by)
                SELECT :organizationId, :clientId, id, :now, :actor FROM client_contacts
                WHERE organization_id = :organizationId AND client_id = :clientId AND id = :contactId
                    AND archived_at IS NULL
                """).param("organizationId", organizationId).param("clientId", clientId)
                .param("contactId", contactId).param("now", timestamp(now)).param("actor", actorId).update();
    }

    @Override
    public void clearPrimaryContact(UUID organizationId, UUID clientId, UUID contactId) {
        jdbc.sql("""
                DELETE FROM client_primary_contacts
                WHERE organization_id = :organizationId AND client_id = :clientId AND contact_id = :contactId
                """).param("organizationId", organizationId).param("clientId", clientId)
                .param("contactId", contactId).update();
    }

    private ClientRecord mapClient(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ClientRecord(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("name"), rs.getString("legal_name"),
                ClientRecord.Status.valueOf(rs.getString("status")), rs.getString("website"),
                rs.getString("email"), rs.getString("phone"), rs.getString("notes"),
                instant(rs, "archived_at"), instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private ClientContact mapContact(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ClientContact(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("client_id", UUID.class), rs.getString("name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("job_title"), rs.getBoolean("is_primary"),
                instant(rs, "archived_at"), instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
