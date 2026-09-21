package com.brainserve.clientonboarding.organization.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;

import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.organization.domain.model.Organization;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAccessRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrganizationAccessRepository implements OrganizationAccessRepository {
    private static final String ACCESS_SELECT = """
            SELECT o.id organization_id, o.slug, o.name organization_name, o.status organization_status,
                   o.created_at organization_created_at, o.updated_at organization_updated_at,
                   o.version organization_version,
                   u.id user_id, u.email, u.display_name, u.password_hash, u.principal_type,
                   u.status user_status, u.email_verified_at, u.failed_login_count, u.locked_until,
                   u.credential_version, u.version user_version,
                   ou.id membership_id, ou.status membership_status,
                   r.id role_id, r.name role_name, p.code permission_code
            FROM organizations o
            JOIN organization_users ou ON ou.organization_id = o.id
            JOIN users u ON u.id = ou.user_id
            JOIN roles r ON r.id = ou.role_id AND r.organization_id = o.id AND r.archived_at IS NULL
            LEFT JOIN role_permissions rp ON rp.role_id = r.id
            LEFT JOIN permissions p ON p.id = rp.permission_id
            """;

    private final JdbcClient jdbc;

    public JdbcOrganizationAccessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrganizationAccess> findByEmailAndSlug(String email, String slug) {
        return access(jdbc.sql(ACCESS_SELECT + " WHERE u.email = :email AND o.slug = :slug")
                .param("email", email).param("slug", slug).query(this::map).list());
    }

    @Override
    public Optional<OrganizationAccess> findByUserAndOrganization(UUID userId, UUID organizationId) {
        return access(jdbc.sql(ACCESS_SELECT + " WHERE u.id = :userId AND o.id = :organizationId")
                .param("userId", userId).param("organizationId", organizationId).query(this::map).list());
    }

    private Optional<OrganizationAccess> access(List<AccessRow> rows) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AccessRow first = rows.get(0);
        var permissions = new LinkedHashSet<String>();
        rows.stream().map(AccessRow::permission).filter(java.util.Objects::nonNull).forEach(permissions::add);
        return Optional.of(new OrganizationAccess(first.organization(), first.user(), first.membershipId(),
                first.membershipStatus(), first.roleId(), first.roleName(), Set.copyOf(permissions)));
    }

    private AccessRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        var organization = new Organization(rs.getObject("organization_id", UUID.class), rs.getString("slug"),
                rs.getString("organization_name"), Organization.OrganizationStatus.valueOf(rs.getString("organization_status")),
                instant(rs, "organization_created_at"), instant(rs, "organization_updated_at"),
                rs.getLong("organization_version"));
        var user = new UserAccount(rs.getObject("user_id", UUID.class), rs.getString("email"),
                rs.getString("display_name"), rs.getString("password_hash"),
                UserAccount.PrincipalType.valueOf(rs.getString("principal_type")),
                UserAccount.UserStatus.valueOf(rs.getString("user_status")), instant(rs, "email_verified_at"),
                rs.getInt("failed_login_count"), instant(rs, "locked_until"), rs.getLong("credential_version"),
                rs.getLong("user_version"));
        return new AccessRow(organization, user, rs.getObject("membership_id", UUID.class),
                OrganizationAccess.MembershipStatus.valueOf(rs.getString("membership_status")),
                rs.getObject("role_id", UUID.class), rs.getString("role_name"), rs.getString("permission_code"));
    }

    private record AccessRow(Organization organization, UserAccount user, UUID membershipId,
                             OrganizationAccess.MembershipStatus membershipStatus, UUID roleId,
                             String roleName, String permission) { }
}
