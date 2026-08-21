package com.brainserve.onboarding.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Revalidates client JWTs against live session, identity, tenant and client-access state on every request. */
@Service
public class ClientTenantAccessService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ClientTenantAccessService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ClientPrincipal loadPrincipal(Jwt jwt) {
        UUID userId = requiredUuid(jwt, "sub");
        UUID organizationId = requiredUuid(jwt, "org");
        UUID sessionId = requiredUuid(jwt, "sid");
        long credentialsVersion = requiredLong(jwt, "cv");

        PrincipalRow row = jdbc.query("""
                SELECT s.expires_at, s.revoked_at, s.credentials_version AS session_credentials_version,
                       u.email, u.display_name, u.status AS user_status,
                       u.credentials_version AS user_credentials_version,
                       o.name AS organization_name, o.slug AS organization_slug, o.status AS organization_status,
                       EXISTS (
                           SELECT 1 FROM client_onboarding.client_users cu
                           WHERE cu.organization_id = s.organization_id
                             AND cu.user_id = s.user_id
                             AND cu.status = 'ACTIVE'
                       ) AS has_access
                FROM client_onboarding.client_auth_sessions s
                JOIN client_onboarding.users u ON u.id = s.user_id
                JOIN client_onboarding.organizations o ON o.id = s.organization_id
                WHERE s.id = ? AND s.user_id = ? AND s.organization_id = ?
                """, rs -> rs.next() ? new PrincipalRow(
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getLong("session_credentials_version"), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("user_status"), rs.getLong("user_credentials_version"),
                        rs.getString("organization_name"), rs.getString("organization_slug"),
                        rs.getString("organization_status"), rs.getBoolean("has_access")) : null,
                sessionId, userId, organizationId);

        Instant now = clock.instant();
        if (row == null || row.revokedAt() != null || !row.expiresAt().isAfter(now)
                || row.sessionCredentialsVersion() != credentialsVersion
                || row.userCredentialsVersion() != credentialsVersion
                || !"ACTIVE".equals(row.userStatus())
                || !"ACTIVE".equals(row.organizationStatus())
                || !row.hasAccess()) {
            throw new BadCredentialsException("The authenticated client session is no longer valid");
        }
        return new ClientPrincipal(userId, organizationId, sessionId, row.email(), row.displayName(),
                row.organizationName(), row.organizationSlug(), Set.of("CLIENT_PORTAL"));
    }

    private static UUID requiredUuid(Jwt jwt, String claim) {
        String value = "sub".equals(claim) ? jwt.getSubject() : jwt.getClaimAsString(claim);
        try { return UUID.fromString(value); }
        catch (RuntimeException ex) { throw new BadCredentialsException("Access token contains an invalid claim", ex); }
    }

    private static long requiredLong(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (value instanceof Number number) return number.longValue();
        throw new BadCredentialsException("Access token contains an invalid claim");
    }

    private record PrincipalRow(Instant expiresAt, Instant revokedAt, long sessionCredentialsVersion,
                                String email, String displayName, String userStatus, long userCredentialsVersion,
                                String organizationName, String organizationSlug, String organizationStatus,
                                boolean hasAccess) {}
}
