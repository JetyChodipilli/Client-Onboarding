package com.brainserve.onboarding.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class TenantAccessService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TenantAccessService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public TenantPrincipal loadPrincipal(Jwt jwt) {
        UUID userId = requiredUuid(jwt, "sub");
        UUID organizationId = requiredUuid(jwt, "org");
        UUID membershipId = requiredUuid(jwt, "mid");
        UUID sessionId = requiredUuid(jwt, "sid");
        long credentialsVersion = requiredLong(jwt, "cv");

        PrincipalRow row = jdbc.query("""
                SELECT s.id AS session_id,
                       s.expires_at,
                       s.revoked_at,
                       s.credentials_version AS session_credentials_version,
                       u.email,
                       u.display_name,
                       u.status AS user_status,
                       u.credentials_version AS user_credentials_version,
                       u.mfa_enabled,
                       m.status AS membership_status,
                       o.name AS organization_name,
                       o.slug AS organization_slug,
                       o.status AS organization_status
                FROM client_onboarding.auth_sessions s
                JOIN client_onboarding.users u ON u.id = s.user_id
                JOIN client_onboarding.organization_users m
                  ON m.id = s.organization_user_id AND m.organization_id = s.organization_id AND m.user_id = s.user_id
                JOIN client_onboarding.organizations o ON o.id = s.organization_id
                WHERE s.id = ? AND s.user_id = ? AND s.organization_id = ? AND s.organization_user_id = ?
                """, rs -> rs.next() ? new PrincipalRow(
                        rs.getObject("session_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getLong("session_credentials_version"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("user_status"),
                        rs.getLong("user_credentials_version"),
                        rs.getBoolean("mfa_enabled"),
                        rs.getString("membership_status"),
                        rs.getString("organization_name"),
                        rs.getString("organization_slug"),
                        rs.getString("organization_status")) : null,
                sessionId, userId, organizationId, membershipId);

        Instant now = clock.instant();
        if (row == null
                || row.revokedAt() != null
                || !row.expiresAt().isAfter(now)
                || !"ACTIVE".equals(row.userStatus())
                || !"ACTIVE".equals(row.membershipStatus())
                || !"ACTIVE".equals(row.organizationStatus())
                || row.userCredentialsVersion() != credentialsVersion
                || row.sessionCredentialsVersion() != credentialsVersion) {
            throw new BadCredentialsException("The authenticated session is no longer valid");
        }

        Set<String> permissions = Set.copyOf(jdbc.query("""
                SELECT DISTINCT p.code
                FROM client_onboarding.organization_user_roles our
                JOIN client_onboarding.roles r
                  ON r.id = our.role_id AND r.organization_id = our.organization_id AND r.status = 'ACTIVE'
                JOIN client_onboarding.role_permissions rp
                  ON rp.role_id = r.id AND rp.organization_id = r.organization_id
                JOIN client_onboarding.permissions p ON p.id = rp.permission_id
                WHERE our.organization_id = ? AND our.organization_user_id = ?
                """, (rs, rowNum) -> rs.getString(1), organizationId, membershipId));

        if (!row.mfaEnabled() && PrivilegedPermissionPolicy.requiresMfa(permissions)) {
            throw new BadCredentialsException("Multi-factor authentication is required for this privileged membership");
        }

        return new TenantPrincipal(
                userId, organizationId, membershipId, sessionId,
                row.email(), row.displayName(), row.organizationName(), row.organizationSlug(), permissions);
    }

    private static UUID requiredUuid(Jwt jwt, String claim) {
        String value = "sub".equals(claim) ? jwt.getSubject() : jwt.getClaimAsString(claim);
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Access token contains an invalid claim", ex);
        }
    }

    private static long requiredLong(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (value instanceof Number number) return number.longValue();
        throw new BadCredentialsException("Access token contains an invalid claim");
    }

    private record PrincipalRow(
            UUID sessionId,
            Instant expiresAt,
            Instant revokedAt,
            long sessionCredentialsVersion,
            String email,
            String displayName,
            String userStatus,
            long userCredentialsVersion,
            boolean mfaEnabled,
            String membershipStatus,
            String organizationName,
            String organizationSlug,
            String organizationStatus) {}
}
