package com.brainserve.clientonboarding.organization.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.organization.domain.model.Organization;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationMember;
import com.brainserve.clientonboarding.organization.domain.model.Role;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAdminRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOrganizationAdminRepository implements OrganizationAdminRepository {
    private static final String ROLE_SELECT = """
            SELECT r.id, r.organization_id, r.name, r.description, r.archived_at, r.version,
                   p.code permission_code
            FROM roles r
            LEFT JOIN role_permissions rp ON rp.role_id = r.id
            LEFT JOIN permissions p ON p.id = rp.permission_id
            """;
    private static final int SETTINGS_LIST_LIMIT = 100;

    private final JdbcClient jdbc;

    public JdbcOrganizationAdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Organization> findOrganizationByIdAndTenant(UUID id, UUID tenantId) {
        return jdbc.sql("SELECT * FROM organizations WHERE id = :id AND id = :tenantId")
                .param("id", id).param("tenantId", tenantId).query(this::mapOrganization).optional();
    }

    @Override
    public Optional<Organization> findOrganizationBySlug(String slug) {
        return jdbc.sql("SELECT * FROM organizations WHERE slug = :slug")
                .param("slug", slug).query(this::mapOrganization).optional();
    }

    @Override
    public Organization insertOrganization(Organization organization, UUID actorId) {
        jdbc.sql("""
                INSERT INTO organizations (id, slug, name, status, created_at, created_by,
                    updated_at, updated_by, version)
                VALUES (:id, :slug, :name, :status, :createdAt, :actor, :updatedAt, :actor, :version)
                """).param("id", organization.id()).param("slug", organization.slug())
                .param("name", organization.name()).param("status", organization.status().name())
                .param("createdAt", timestamp(organization.createdAt()))
                .param("updatedAt", timestamp(organization.updatedAt()))
                .param("actor", actorId).param("version", organization.version()).update();
        return organization;
    }

    @Override
    public boolean updateOrganization(UUID id, UUID tenantId, String name, long version,
                                      UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE organizations SET name = :name, updated_at = :now, updated_by = :actor,
                    version = version + 1
                WHERE id = :id AND id = :tenantId AND version = :version AND status = 'ACTIVE'
                """).param("name", name).param("now", timestamp(now)).param("actor", actorId)
                .param("id", id).param("tenantId", tenantId).param("version", version).update() == 1;
    }

    @Override
    public List<String> listPermissionCodes() {
        return jdbc.sql("SELECT code FROM permissions ORDER BY code LIMIT :limit")
                .param("limit", SETTINGS_LIST_LIMIT).query(String.class).list();
    }

    @Override
    public List<Role> listRoles(UUID organizationId) {
        return roles(jdbc.sql("""
                SELECT r.id, r.organization_id, r.name, r.description, r.archived_at, r.version,
                       p.code permission_code
                FROM (SELECT * FROM roles WHERE organization_id = :organizationId
                      ORDER BY name, id LIMIT :limit) r
                LEFT JOIN role_permissions rp ON rp.role_id = r.id
                LEFT JOIN permissions p ON p.id = rp.permission_id
                ORDER BY r.name, r.id, p.code
                """).param("organizationId", organizationId).param("limit", SETTINGS_LIST_LIMIT)
                .query(this::mapRoleRow).list());
    }

    @Override
    public Optional<Role> findRole(UUID organizationId, UUID roleId) {
        List<Role> roles = roles(jdbc.sql(ROLE_SELECT + " WHERE r.organization_id = :organizationId AND r.id = :roleId")
                .param("organizationId", organizationId).param("roleId", roleId).query(this::mapRoleRow).list());
        return roles.stream().findFirst();
    }

    @Override
    @Transactional
    public Role insertRole(Role role, UUID actorId, Instant now) {
        jdbc.sql("""
                INSERT INTO roles (id, organization_id, name, description, archived_at,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :name, :description, NULL, :now, :actor, :now, :actor, 0)
                """).param("id", role.id()).param("organizationId", role.organizationId())
                .param("name", role.name()).param("description", role.description())
                .param("now", timestamp(now)).param("actor", actorId).update();
        replacePermissions(role.id(), role.permissions(), actorId, now);
        return role;
    }

    @Override
    @Transactional
    public boolean updateRole(UUID organizationId, UUID roleId, String name, String description,
                              Set<String> permissions, long version, UUID actorId, Instant now) {
        int updated = jdbc.sql("""
                UPDATE roles SET name = :name, description = :description, updated_at = :now,
                    updated_by = :actor, version = version + 1
                WHERE id = :roleId AND organization_id = :organizationId
                    AND version = :version AND archived_at IS NULL
                """).param("name", name).param("description", description).param("now", timestamp(now))
                .param("actor", actorId).param("roleId", roleId).param("organizationId", organizationId)
                .param("version", version).update();
        if (updated == 1) {
            replacePermissions(roleId, permissions, actorId, now);
        }
        return updated == 1;
    }

    @Override
    public boolean archiveRole(UUID organizationId, UUID roleId, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE roles SET archived_at = :now, updated_at = :now, updated_by = :actor,
                    version = version + 1
                WHERE id = :roleId AND organization_id = :organizationId
                    AND version = :version AND archived_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM organization_users ou
                        WHERE ou.role_id = roles.id AND ou.status IN ('ACTIVE', 'INVITED'))
                """).param("now", timestamp(now)).param("actor", actorId).param("roleId", roleId)
                .param("organizationId", organizationId).param("version", version).update() == 1;
    }

    @Override
    public List<OrganizationMember> listMembers(UUID organizationId) {
        return jdbc.sql("""
                SELECT ou.id, ou.organization_id, ou.user_id, u.email, u.display_name,
                       ou.role_id, r.name role_name, ou.status, ou.invited_at, ou.joined_at, ou.version
                FROM organization_users ou
                JOIN users u ON u.id = ou.user_id
                JOIN roles r ON r.id = ou.role_id AND r.organization_id = ou.organization_id
                WHERE ou.organization_id = :organizationId
                ORDER BY u.display_name, u.email
                LIMIT :limit
                """).param("organizationId", organizationId).param("limit", SETTINGS_LIST_LIMIT)
                .query(this::mapMember).list();
    }

    @Override
    public Optional<OrganizationMember> findMember(UUID organizationId, UUID membershipId) {
        return jdbc.sql("""
                SELECT ou.id, ou.organization_id, ou.user_id, u.email, u.display_name,
                       ou.role_id, r.name role_name, ou.status, ou.invited_at, ou.joined_at, ou.version
                FROM organization_users ou
                JOIN users u ON u.id = ou.user_id
                JOIN roles r ON r.id = ou.role_id AND r.organization_id = ou.organization_id
                WHERE ou.organization_id = :organizationId AND ou.id = :membershipId
                """).param("organizationId", organizationId).param("membershipId", membershipId)
                .query(this::mapMember).optional();
    }

    @Override
    public OrganizationMember insertMember(OrganizationMember member, UUID actorId, Instant now) {
        jdbc.sql("""
                INSERT INTO organization_users (id, organization_id, user_id, role_id, status,
                    invited_at, joined_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :userId, :roleId, :status, :invitedAt, :joinedAt,
                    :now, :actor, :now, :actor, :version)
                """).param("id", member.id()).param("organizationId", member.organizationId())
                .param("userId", member.userId()).param("roleId", member.roleId())
                .param("status", member.status().name()).param("invitedAt", timestamp(member.invitedAt()))
                .param("joinedAt", timestamp(member.joinedAt())).param("now", timestamp(now)).param("actor", actorId)
                .param("version", member.version()).update();
        return member;
    }

    @Override
    public boolean updateMember(UUID organizationId, UUID membershipId, UUID roleId, String status,
                                long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE organization_users SET role_id = :roleId, status = :status,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE id = :membershipId AND organization_id = :organizationId AND version = :version
                    AND EXISTS (SELECT 1 FROM roles r WHERE r.id = :roleId
                        AND r.organization_id = :organizationId AND r.archived_at IS NULL)
                """).param("roleId", roleId).param("status", status).param("now", timestamp(now))
                .param("actor", actorId).param("membershipId", membershipId)
                .param("organizationId", organizationId).param("version", version).update() == 1;
    }

    @Override
    public boolean activateMembership(UUID organizationId, UUID membershipId, UUID userId, Instant now) {
        return jdbc.sql("""
                UPDATE organization_users SET status = 'ACTIVE', joined_at = COALESCE(joined_at, :now),
                    updated_at = :now, updated_by = :userId, version = version + 1
                WHERE id = :membershipId AND organization_id = :organizationId
                    AND user_id = :userId AND status = 'INVITED'
                """).param("now", timestamp(now)).param("userId", userId).param("membershipId", membershipId)
                .param("organizationId", organizationId).update() == 1;
    }

    @Override
    public long countActiveManagers(UUID organizationId, String... requiredPermissions) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM organization_users ou
                WHERE ou.organization_id = :organizationId AND ou.status = 'ACTIVE'
                  AND (SELECT COUNT(DISTINCT p.code) FROM role_permissions rp
                       JOIN permissions p ON p.id = rp.permission_id
                       WHERE rp.role_id = ou.role_id AND p.code IN (:codes)) = :requiredCount
                """).param("organizationId", organizationId)
                .param("codes", Arrays.asList(requiredPermissions))
                .param("requiredCount", requiredPermissions.length).query(Long.class).single();
    }

    @Override
    public long countActiveMembersForRole(UUID organizationId, UUID roleId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM organization_users
                WHERE organization_id = :organizationId AND role_id = :roleId AND status = 'ACTIVE'
                """).param("organizationId", organizationId).param("roleId", roleId)
                .query(Long.class).single();
    }

    private void replacePermissions(UUID roleId, Set<String> permissions, UUID actorId, Instant now) {
        jdbc.sql("DELETE FROM role_permissions WHERE role_id = :roleId").param("roleId", roleId).update();
        for (String permission : permissions) {
            int inserted = jdbc.sql("""
                    INSERT INTO role_permissions (role_id, permission_id, created_at, created_by)
                    SELECT :roleId, id, :now, :actor FROM permissions WHERE code = :code
                    """).param("roleId", roleId).param("now", timestamp(now)).param("actor", actorId)
                    .param("code", permission).update();
            if (inserted != 1) {
                throw new IllegalArgumentException("Unknown permission: " + permission);
            }
        }
    }

    private Organization mapOrganization(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Organization(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                Organization.OrganizationStatus.valueOf(rs.getString("status")), instant(rs, "created_at"),
                instant(rs, "updated_at"), rs.getLong("version"));
    }

    private RoleRow mapRoleRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RoleRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("name"), rs.getString("description"), rs.getString("permission_code"),
                instant(rs, "archived_at"), rs.getLong("version"));
    }

    private List<Role> roles(List<RoleRow> rows) {
        Map<UUID, RoleAccumulator> grouped = new LinkedHashMap<>();
        for (RoleRow row : rows) {
            var accumulator = grouped.computeIfAbsent(row.id(), ignored -> new RoleAccumulator(row));
            if (row.permission() != null) accumulator.permissions.add(row.permission());
        }
        var result = new ArrayList<Role>();
        grouped.values().forEach(value -> result.add(value.toRole()));
        return result;
    }

    private OrganizationMember mapMember(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrganizationMember(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                rs.getObject("role_id", UUID.class), rs.getString("role_name"),
                OrganizationAccess.MembershipStatus.valueOf(rs.getString("status")), instant(rs, "invited_at"),
                instant(rs, "joined_at"), rs.getLong("version"));
    }

    private record RoleRow(UUID id, UUID organizationId, String name, String description,
                           String permission, Instant archivedAt, long version) { }

    private static final class RoleAccumulator {
        private final RoleRow row;
        private final Set<String> permissions = new LinkedHashSet<>();
        private RoleAccumulator(RoleRow row) { this.row = row; }
        private Role toRole() {
            return new Role(row.id(), row.organizationId(), row.name(), row.description(),
                    Set.copyOf(permissions), row.archivedAt(), row.version());
        }
    }
}
