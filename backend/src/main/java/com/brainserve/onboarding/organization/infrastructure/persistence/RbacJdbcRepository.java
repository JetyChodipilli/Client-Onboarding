package com.brainserve.onboarding.organization.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RbacJdbcRepository {

    private final JdbcTemplate jdbc;

    public RbacJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> permissionCodes(UUID organizationId, UUID membershipId) {
        List<String> values = jdbc.query("""
                SELECT DISTINCT p.code
                FROM client_onboarding.organization_user_roles our
                JOIN client_onboarding.roles r
                  ON r.id = our.role_id AND r.organization_id = our.organization_id AND r.status = 'ACTIVE'
                JOIN client_onboarding.role_permissions rp
                  ON rp.role_id = r.id AND rp.organization_id = r.organization_id
                JOIN client_onboarding.permissions p ON p.id = rp.permission_id
                WHERE our.organization_id = ? AND our.organization_user_id = ?
                ORDER BY p.code
                """, (rs, rowNum) -> rs.getString(1), organizationId, membershipId);
        return new LinkedHashSet<>(values);
    }

    public List<UUID> roleIds(UUID organizationId, UUID membershipId) {
        return jdbc.query("""
                SELECT role_id FROM client_onboarding.organization_user_roles
                WHERE organization_id = ? AND organization_user_id = ?
                ORDER BY role_id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), organizationId, membershipId);
    }

    public Set<String> permissionCodesForRole(UUID organizationId, UUID roleId) {
        return new LinkedHashSet<>(jdbc.query("""
                SELECT p.code
                FROM client_onboarding.role_permissions rp
                JOIN client_onboarding.permissions p ON p.id = rp.permission_id
                WHERE rp.organization_id = ? AND rp.role_id = ?
                ORDER BY p.code
                """, (rs, rowNum) -> rs.getString(1), organizationId, roleId));
    }

    public Set<String> permissionCodesForRoles(UUID organizationId, Set<UUID> roleIds) {
        if (roleIds.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(roleIds.size(), "?"));
        Object[] args = new Object[roleIds.size() + 1];
        args[0] = organizationId;
        int i = 1;
        for (UUID roleId : roleIds) args[i++] = roleId;
        return new LinkedHashSet<>(jdbc.query(
                "SELECT DISTINCT p.code FROM client_onboarding.roles r " +
                "JOIN client_onboarding.role_permissions rp ON rp.role_id = r.id AND rp.organization_id = r.organization_id " +
                "JOIN client_onboarding.permissions p ON p.id = rp.permission_id " +
                "WHERE r.organization_id = ? AND r.status = 'ACTIVE' AND r.id IN (" + placeholders + ") ORDER BY p.code",
                (rs, rowNum) -> rs.getString(1), args));
    }

    public long countActiveMembersWithPermission(UUID organizationId, String permissionCode) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT m.id)
                FROM client_onboarding.organization_users m
                JOIN client_onboarding.organization_user_roles our
                  ON our.organization_id = m.organization_id AND our.organization_user_id = m.id
                JOIN client_onboarding.roles r
                  ON r.organization_id = our.organization_id AND r.id = our.role_id AND r.status = 'ACTIVE'
                JOIN client_onboarding.role_permissions rp
                  ON rp.organization_id = r.organization_id AND rp.role_id = r.id
                JOIN client_onboarding.permissions p ON p.id = rp.permission_id
                WHERE m.organization_id = ? AND m.status = 'ACTIVE' AND p.code = ?
                """, Long.class, organizationId, permissionCode);
        return count == null ? 0 : count;
    }


    public long countActiveMembersWithPermissionExcludingRole(UUID organizationId, String permissionCode, UUID excludedRoleId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT m.id)
                FROM client_onboarding.organization_users m
                WHERE m.organization_id = ? AND m.status = 'ACTIVE'
                  AND EXISTS (
                    SELECT 1
                    FROM client_onboarding.organization_user_roles our
                    JOIN client_onboarding.roles r
                      ON r.organization_id = our.organization_id AND r.id = our.role_id AND r.status = 'ACTIVE'
                    JOIN client_onboarding.role_permissions rp
                      ON rp.organization_id = r.organization_id AND rp.role_id = r.id
                    JOIN client_onboarding.permissions p ON p.id = rp.permission_id
                    WHERE our.organization_id = m.organization_id
                      AND our.organization_user_id = m.id
                      AND r.id <> ?
                      AND p.code = ?
                  )
                """, Long.class, organizationId, excludedRoleId, permissionCode);
        return count == null ? 0 : count;
    }

    public List<RolePermissionRow> rolePermissions(UUID organizationId) {
        return jdbc.query("""
                SELECT r.id AS role_id, p.code AS permission_code
                FROM client_onboarding.roles r
                LEFT JOIN client_onboarding.role_permissions rp
                  ON rp.role_id = r.id AND rp.organization_id = r.organization_id
                LEFT JOIN client_onboarding.permissions p ON p.id = rp.permission_id
                WHERE r.organization_id = ? AND r.status = 'ACTIVE'
                ORDER BY r.name, p.code
                """, (rs, rowNum) -> mapRolePermission(rs), organizationId);
    }

    public void replaceRolePermissions(UUID organizationId, UUID roleId, Set<UUID> permissionIds, UUID actorId) {
        jdbc.update("DELETE FROM client_onboarding.role_permissions WHERE organization_id = ? AND role_id = ?",
                organizationId, roleId);
        for (UUID permissionId : permissionIds) {
            jdbc.update("""
                    INSERT INTO client_onboarding.role_permissions
                        (organization_id, role_id, permission_id, created_by)
                    VALUES (?, ?, ?, ?)
                    """, organizationId, roleId, permissionId, actorId);
        }
    }

    public void replaceMembershipRoles(UUID organizationId, UUID membershipId, Set<UUID> roleIds, UUID actorId) {
        jdbc.update("DELETE FROM client_onboarding.organization_user_roles WHERE organization_id = ? AND organization_user_id = ?",
                organizationId, membershipId);
        for (UUID roleId : roleIds) {
            jdbc.update("""
                    INSERT INTO client_onboarding.organization_user_roles
                        (organization_id, organization_user_id, role_id, created_by)
                    VALUES (?, ?, ?, ?)
                    """, organizationId, membershipId, roleId, actorId);
        }
    }

    public void addInvitationRole(UUID organizationId, UUID invitationId, UUID roleId) {
        jdbc.update("""
                INSERT INTO client_onboarding.organization_invitation_roles
                    (organization_id, invitation_id, role_id)
                VALUES (?, ?, ?)
                """, organizationId, invitationId, roleId);
    }

    public List<UUID> invitationRoleIds(UUID organizationId, UUID invitationId) {
        return jdbc.query("""
                SELECT role_id FROM client_onboarding.organization_invitation_roles
                WHERE organization_id = ? AND invitation_id = ?
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), organizationId, invitationId);
    }

    public long countPermissionIds(Set<UUID> permissionIds) {
        if (permissionIds.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(permissionIds.size(), "?"));
        Object[] args = permissionIds.toArray();
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_onboarding.permissions WHERE id IN (" + placeholders + ")",
                Long.class, args);
        return count == null ? 0 : count;
    }

    public long countTenantRoles(UUID organizationId, Set<UUID> roleIds) {
        if (roleIds.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(roleIds.size(), "?"));
        Object[] args = new Object[roleIds.size() + 1];
        args[0] = organizationId;
        int i = 1;
        for (UUID roleId : roleIds) args[i++] = roleId;
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_onboarding.roles WHERE organization_id = ? AND status = 'ACTIVE' AND id IN (" + placeholders + ")",
                Long.class, args);
        return count == null ? 0 : count;
    }

    private static RolePermissionRow mapRolePermission(ResultSet rs) throws SQLException {
        return new RolePermissionRow(rs.getObject("role_id", UUID.class), rs.getString("permission_code"));
    }

    public record RolePermissionRow(UUID roleId, String permissionCode) {}
}
