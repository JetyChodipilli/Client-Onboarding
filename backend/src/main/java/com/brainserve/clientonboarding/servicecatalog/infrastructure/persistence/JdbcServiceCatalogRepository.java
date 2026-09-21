package com.brainserve.clientonboarding.servicecatalog.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.servicecatalog.domain.model.ServiceDefinition;
import com.brainserve.clientonboarding.servicecatalog.domain.repository.ServiceCatalogRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcServiceCatalogRepository implements ServiceCatalogRepository {
    private final JdbcClient jdbc;

    public JdbcServiceCatalogRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public PageSlice<ServiceDefinition> findPage(UUID organizationId, String search, String status,
                                                  int page, int size) {
        String where = " WHERE organization_id = :organizationId AND (LOWER(name) LIKE :search OR LOWER(code) LIKE :search)";
        if (status != null) where += " AND status = :status";
        var items = jdbc.sql("SELECT * FROM services" + where + " ORDER BY name, id LIMIT :limit OFFSET :offset")
                .param("organizationId", organizationId).param("search", "%" + search.toLowerCase() + "%")
                .param("limit", size).param("offset", page * size);
        var count = jdbc.sql("SELECT COUNT(*) FROM services" + where)
                .param("organizationId", organizationId).param("search", "%" + search.toLowerCase() + "%");
        if (status != null) { items = items.param("status", status); count = count.param("status", status); }
        return new PageSlice<>(items.query(this::map).list(), page, size, count.query(Long.class).single());
    }

    @Override
    public Optional<ServiceDefinition> findById(UUID organizationId, UUID serviceId) {
        return jdbc.sql("SELECT * FROM services WHERE organization_id = :organizationId AND id = :id")
                .param("organizationId", organizationId).param("id", serviceId).query(this::map).optional();
    }

    @Override
    public ServiceDefinition insert(ServiceDefinition service, UUID actorId) {
        jdbc.sql("""
                INSERT INTO services (id, organization_id, code, name, description, status, archived_at,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :code, :name, :description, :status, NULL,
                    :createdAt, :actor, :updatedAt, :actor, 0)
                """).param("id", service.id()).param("organizationId", service.organizationId())
                .param("code", service.code()).param("name", service.name()).param("description", service.description())
                .param("status", service.status().name()).param("createdAt", timestamp(service.createdAt()))
                .param("updatedAt", timestamp(service.updatedAt())).param("actor", actorId).update();
        return service;
    }

    @Override
    public boolean update(UUID organizationId, UUID serviceId, ServiceDefinition replacement, long version,
                          UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE services SET code = :code, name = :name, description = :description, status = :status,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL
                """).param("code", replacement.code()).param("name", replacement.name())
                .param("description", replacement.description()).param("status", replacement.status().name())
                .param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", serviceId).param("version", version).update() == 1;
    }

    @Override
    public boolean archive(UUID organizationId, UUID serviceId, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE services SET status = 'ARCHIVED', archived_at = :now, updated_at = :now,
                    updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                    AND archived_at IS NULL AND NOT EXISTS (SELECT 1 FROM projects p
                        WHERE p.organization_id = :organizationId AND p.service_id = services.id
                            AND p.status NOT IN ('CANCELLED', 'COMPLETED', 'ARCHIVED'))
                """).param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", serviceId).param("version", version).update() == 1;
    }

    private ServiceDefinition map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ServiceDefinition(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getString("description"),
                ServiceDefinition.Status.valueOf(rs.getString("status")), instant(rs, "archived_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }
}
