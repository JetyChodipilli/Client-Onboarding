package com.brainserve.clientonboarding.portal.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.instant;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;

import com.brainserve.clientonboarding.auth.application.ClientSessionAccessPort;
import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.portal.domain.model.ClientInvitation;
import com.brainserve.clientonboarding.portal.domain.repository.PortalRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPortalRepository implements PortalRepository, ClientSessionAccessPort {
    private static final Set<String> MEMBER_PERMISSIONS = Set.of("CLIENT_PORTAL_READ", "CLIENT_PORTAL_STEP_UPDATE");
    private final JdbcClient jdbc;

    public JdbcPortalRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<InvitationTarget> findInvitationTarget(UUID organizationId, UUID onboardingId, UUID contactId) {
        return jdbc.sql("""
                SELECT o.id organization_id, o.name organization_name, o.slug organization_slug,
                       oi.id onboarding_id, oi.status onboarding_status, oi.version onboarding_version,
                       p.id project_id, p.name project_name, c.id client_id, c.name client_name,
                       cc.id contact_id, cc.name contact_name, cc.email contact_email
                FROM onboarding_instances oi
                JOIN organizations o ON o.id = oi.organization_id AND o.status = 'ACTIVE'
                JOIN projects p ON p.organization_id = oi.organization_id AND p.id = oi.project_id
                JOIN clients c ON c.organization_id = p.organization_id AND c.id = p.client_id
                JOIN client_contacts cc ON cc.organization_id = c.organization_id AND cc.client_id = c.id
                WHERE oi.organization_id = :organizationId AND oi.id = :onboardingId
                  AND cc.id = :contactId AND cc.archived_at IS NULL AND c.archived_at IS NULL
                """).param("organizationId", organizationId).param("onboardingId", onboardingId)
                .param("contactId", contactId).query((rs, rowNum) -> new InvitationTarget(
                        rs.getObject("organization_id", UUID.class), rs.getString("organization_name"),
                        rs.getString("organization_slug"), rs.getObject("onboarding_id", UUID.class),
                        rs.getString("onboarding_status"), rs.getLong("onboarding_version"),
                        rs.getObject("project_id", UUID.class), rs.getString("project_name"),
                        rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                        rs.getObject("contact_id", UUID.class), rs.getString("contact_name"),
                        rs.getString("contact_email"))).optional();
    }

    @Override
    public Optional<ClientInvitation> findInvitation(UUID organizationId, UUID invitationId) {
        return jdbc.sql("SELECT * FROM client_invitations WHERE organization_id = :organizationId AND id = :id")
                .param("organizationId", organizationId).param("id", invitationId)
                .query(this::mapInvitation).optional();
    }

    @Override
    public Optional<ClientInvitation> findInvitationByTokenHash(String tokenHash) {
        return jdbc.sql("SELECT * FROM client_invitations WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash).query(this::mapInvitation).optional();
    }

    @Override
    public List<ClientInvitation> findInvitations(UUID organizationId, UUID onboardingId) {
        return jdbc.sql("""
                SELECT * FROM client_invitations WHERE organization_id = :organizationId
                  AND onboarding_id = :onboardingId ORDER BY created_at DESC, id DESC
                """).param("organizationId", organizationId).param("onboardingId", onboardingId)
                .query(this::mapInvitation).list();
    }

    @Override
    public void insertInvitation(ClientInvitation value, UUID actorId) {
        jdbc.sql("""
                INSERT INTO client_invitations (id, organization_id, client_id, contact_id, project_id,
                    onboarding_id, invited_email, client_role, token_hash, status, delivery_status,
                    delivery_error, expires_at, sent_at, accepted_at, accepted_by, revoked_at, resend_count,
                    created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :clientId, :contactId, :projectId, :onboardingId, :email,
                    :role, :tokenHash, 'PENDING', 'PENDING', NULL, :expiresAt, NULL, NULL, NULL, NULL, 0,
                    :now, :actor, :now, :actor, 0)
                """).param("id", value.id()).param("organizationId", value.organizationId())
                .param("clientId", value.clientId()).param("contactId", value.contactId())
                .param("projectId", value.projectId()).param("onboardingId", value.onboardingId())
                .param("email", value.invitedEmail()).param("role", value.clientRole().name())
                .param("tokenHash", value.tokenHash()).param("expiresAt", timestamp(value.expiresAt()))
                .param("now", timestamp(value.createdAt())).param("actor", actorId).update();
    }

    @Override
    public boolean rotateInvitation(UUID organizationId, UUID invitationId, String tokenHash, Instant expiresAt,
                                    long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE client_invitations SET token_hash = :tokenHash, expires_at = :expiresAt,
                    delivery_status = 'PENDING', delivery_error = NULL, sent_at = NULL,
                    resend_count = resend_count + 1, updated_at = :now, updated_by = :actor,
                    version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                  AND status = 'PENDING' AND accepted_at IS NULL AND revoked_at IS NULL
                """).param("tokenHash", tokenHash).param("expiresAt", timestamp(expiresAt))
                .param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", invitationId).param("version", version).update() == 1;
    }

    @Override
    public boolean revokeInvitation(UUID organizationId, UUID invitationId, long version, UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE client_invitations SET status = 'REVOKED', pending_guard = NULL, revoked_at = :now,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                  AND status = 'PENDING'
                """).param("now", timestamp(now)).param("actor", actorId).param("organizationId", organizationId)
                .param("id", invitationId).param("version", version).update() == 1;
    }

    @Override
    public void markDelivery(UUID organizationId, UUID invitationId, ClientInvitation.DeliveryStatus status,
                             String error, UUID actorId, Instant now) {
        jdbc.sql("""
                UPDATE client_invitations SET delivery_status = :status, delivery_error = :error,
                    sent_at = CASE WHEN :status = 'SENT' THEN :now ELSE sent_at END,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND status = 'PENDING'
                """).param("status", status.name()).param("error", error).param("now", timestamp(now))
                .param("actor", actorId).param("organizationId", organizationId).param("id", invitationId).update();
    }

    @Override
    public Optional<ClientUserLink> findClientUserLink(UUID organizationId, UUID userId) {
        return jdbc.sql("""
                SELECT id, client_id, status FROM client_users
                WHERE organization_id = :organizationId AND user_id = :userId
                """).param("organizationId", organizationId).param("userId", userId)
                .query((rs, rowNum) -> new ClientUserLink(rs.getObject("id", UUID.class),
                        rs.getObject("client_id", UUID.class), rs.getString("status"))).optional();
    }

    @Override
    public UUID activateClientUser(ClientInvitation invitation, UUID userId, UUID actorId, Instant now) {
        Optional<ClientUserLink> existing = findClientUserLink(invitation.organizationId(), userId);
        if (existing.isPresent()) {
            jdbc.sql("""
                    UPDATE client_users SET contact_id = :contactId, client_role = :role, status = 'ACTIVE',
                        activated_at = COALESCE(activated_at, :now), updated_at = :now, updated_by = :actor,
                        version = version + 1
                    WHERE organization_id = :organizationId AND id = :id AND client_id = :clientId
                    """).param("contactId", invitation.contactId()).param("role", invitation.clientRole().name())
                    .param("now", timestamp(now)).param("actor", actorId)
                    .param("organizationId", invitation.organizationId()).param("id", existing.get().id())
                    .param("clientId", invitation.clientId()).update();
            return existing.get().id();
        }
        UUID clientUserId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO client_users (id, organization_id, client_id, contact_id, user_id, status,
                    client_role, activated_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :organizationId, :clientId, :contactId, :userId, 'ACTIVE', :role, :now,
                    :now, :actor, :now, :actor, 0)
                """).param("id", clientUserId).param("organizationId", invitation.organizationId())
                .param("clientId", invitation.clientId()).param("contactId", invitation.contactId())
                .param("userId", userId).param("role", invitation.clientRole().name())
                .param("now", timestamp(now)).param("actor", actorId).update();
        return clientUserId;
    }

    @Override
    public void grantProjectAccess(UUID organizationId, UUID clientId, UUID clientUserId, UUID projectId,
                                   UUID actorId, Instant now) {
        jdbc.sql("""
                INSERT INTO client_user_project_access (organization_id, client_id, client_user_id,
                    project_id, granted_at, granted_by)
                SELECT :organizationId, :clientId, :clientUserId, :projectId, :now, :actor
                WHERE NOT EXISTS (SELECT 1 FROM client_user_project_access
                    WHERE organization_id = :organizationId AND client_user_id = :clientUserId
                      AND project_id = :projectId)
                """).param("organizationId", organizationId).param("clientId", clientId)
                .param("clientUserId", clientUserId).param("projectId", projectId)
                .param("now", timestamp(now)).param("actor", actorId).update();
    }

    @Override
    public boolean acceptInvitation(UUID organizationId, UUID invitationId, long version, UUID userId,
                                    UUID actorId, Instant now) {
        return jdbc.sql("""
                UPDATE client_invitations SET status = 'ACCEPTED', pending_guard = NULL, accepted_at = :now,
                    accepted_by = :userId, updated_at = :now, updated_by = :actor, version = version + 1
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                  AND status = 'PENDING' AND expires_at > :now AND revoked_at IS NULL
                """).param("now", timestamp(now)).param("userId", userId).param("actor", actorId)
                .param("organizationId", organizationId).param("id", invitationId)
                .param("version", version).update() == 1;
    }

    @Override public Optional<ClientSessionAccess> findByUserAndOrganization(UUID userId, UUID organizationId) {
        return findClientAccess(userId, organizationId);
    }
    @Override public Optional<ClientSessionAccess> findByEmailAndOrganizationSlug(String email, String slug) {
        return findClientAccess(email, slug);
    }

    @Override
    public Optional<ClientSessionAccess> findClientAccess(UUID userId, UUID organizationId) {
        return access("u.id = :userId AND o.id = :organizationId", userId, organizationId);
    }

    @Override
    public Optional<ClientSessionAccess> findClientAccess(String email, String slug) {
        return access("u.email = :email AND o.slug = :slug", email, slug);
    }

    private Optional<ClientSessionAccess> access(String predicate, Object first, Object second) {
        var spec = jdbc.sql("""
                SELECT o.id organization_id, o.name organization_name, o.slug organization_slug,
                       cu.id client_user_id, cu.client_id, cu.client_role, c.name client_name,
                       u.* FROM client_users cu
                JOIN organizations o ON o.id = cu.organization_id
                JOIN clients c ON c.organization_id = cu.organization_id AND c.id = cu.client_id
                JOIN users u ON u.id = cu.user_id
                WHERE """ + predicate + " AND o.status = 'ACTIVE' AND cu.status = 'ACTIVE' AND c.archived_at IS NULL");
        if (first instanceof UUID) spec = spec.param("userId", first).param("organizationId", second);
        else spec = spec.param("email", first).param("slug", second);
        return spec.query((rs, rowNum) -> new ClientSessionAccess(
                rs.getObject("organization_id", UUID.class), rs.getString("organization_name"),
                rs.getString("organization_slug"), rs.getObject("client_user_id", UUID.class),
                rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                rs.getString("client_role"), mapUser(rs), permissions(rs.getString("client_role")))).optional();
    }

    @Override
    public List<PortalProject> findPortalProjects(UUID organizationId, UUID clientUserId) {
        return portalProjectQuery("", organizationId, clientUserId, null).list();
    }

    @Override
    public Optional<PortalProject> findPortalProject(UUID organizationId, UUID clientUserId, UUID projectId) {
        return portalProjectQuery(" AND p.id = :projectId", organizationId, clientUserId, projectId).optional();
    }

    private JdbcClient.MappedQuerySpec<PortalProject> portalProjectQuery(String extra, UUID organizationId,
                                                                         UUID clientUserId, UUID projectId) {
        var spec = jdbc.sql("""
                SELECT p.id project_id, p.name project_name, p.status project_status, c.name client_name,
                       oi.id onboarding_id, oi.status onboarding_status, oi.ready
                FROM client_user_project_access a
                JOIN projects p ON p.organization_id = a.organization_id AND p.id = a.project_id
                JOIN clients c ON c.organization_id = p.organization_id AND c.id = p.client_id
                JOIN onboarding_instances oi ON oi.organization_id = p.organization_id AND oi.project_id = p.id
                WHERE a.organization_id = :organizationId AND a.client_user_id = :clientUserId
                  AND p.status <> 'ARCHIVED'""" + extra + " ORDER BY p.updated_at DESC, p.id")
                .param("organizationId", organizationId).param("clientUserId", clientUserId);
        if (projectId != null) spec = spec.param("projectId", projectId);
        return spec.query((rs, rowNum) -> new PortalProject(rs.getObject("project_id", UUID.class),
                rs.getString("project_name"), rs.getString("project_status"), rs.getString("client_name"),
                rs.getObject("onboarding_id", UUID.class), rs.getString("onboarding_status"),
                rs.getBoolean("ready")));
    }

    @Override
    public Optional<String> findHelpEmail(UUID organizationId, UUID projectId) {
        return jdbc.sql("""
                SELECT u.email FROM project_members pm
                JOIN organization_users ou ON ou.organization_id = pm.organization_id AND ou.id = pm.membership_id
                JOIN users u ON u.id = ou.user_id
                WHERE pm.organization_id = :organizationId AND pm.project_id = :projectId
                  AND ou.status = 'ACTIVE' ORDER BY pm.created_at, pm.id LIMIT 1
                """).param("organizationId", organizationId).param("projectId", projectId)
                .query(String.class).optional();
    }

    private ClientInvitation mapInvitation(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ClientInvitation(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("client_id", UUID.class), rs.getObject("contact_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getObject("onboarding_id", UUID.class),
                rs.getString("invited_email"), ClientInvitation.ClientRole.valueOf(rs.getString("client_role")),
                rs.getString("token_hash"), ClientInvitation.Status.valueOf(rs.getString("status")),
                ClientInvitation.DeliveryStatus.valueOf(rs.getString("delivery_status")),
                instant(rs, "expires_at"), instant(rs, "sent_at"), instant(rs, "accepted_at"),
                rs.getObject("accepted_by", UUID.class), instant(rs, "revoked_at"), rs.getInt("resend_count"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private UserAccount mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserAccount(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                rs.getString("password_hash"), UserAccount.PrincipalType.valueOf(rs.getString("principal_type")),
                UserAccount.UserStatus.valueOf(rs.getString("status")), instant(rs, "email_verified_at"),
                rs.getInt("failed_login_count"), instant(rs, "locked_until"),
                rs.getLong("credential_version"), rs.getLong("version"));
    }

    private Set<String> permissions(String role) {
        return "ADMIN".equals(role)
                ? Set.of("CLIENT_PORTAL_READ", "CLIENT_PORTAL_STEP_UPDATE", "CLIENT_PORTAL_ADMIN")
                : MEMBER_PERMISSIONS;
    }
}
