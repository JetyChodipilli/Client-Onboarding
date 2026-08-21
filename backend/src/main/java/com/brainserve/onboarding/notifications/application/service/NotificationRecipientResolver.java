package com.brainserve.onboarding.notifications.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves notification recipients together with the small amount of delivery metadata needed by the fan-out
 * worker. Preferences are joined here so routing a notification batch does not create an N+1 preference/email
 * lookup pattern in {@link NotificationService}.
 */
@Component
public class NotificationRecipientResolver {
    private final JdbcTemplate jdbc;

    public NotificationRecipientResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Recipient> resolve(
            UUID organizationId,
            String scope,
            String permission,
            UUID projectId,
            JsonNode payload) {
        return switch (scope) {
            case "PROJECT_CLIENTS" -> projectId == null
                    ? List.of()
                    : clientProjectRecipients(organizationId, projectId);
            case "PROJECT_MEMBERS" -> projectId == null
                    ? List.of()
                    : internalProjectRecipients(organizationId, projectId);
            case "PERMISSION" -> permissionRecipients(organizationId, permission);
            case "ACTOR" -> actor(organizationId, payload);
            default -> List.of();
        };
    }

    public List<Recipient> resolveRole(UUID organizationId, UUID roleId) {
        if (roleId == null) return List.of();
        return jdbc.query(
                """
                SELECT DISTINCT u.id,
                                u.email,
                                u.display_name,
                                COALESCE(np.in_app_enabled, TRUE),
                                COALESCE(np.email_enabled, TRUE)
                FROM client_onboarding.organization_user_roles our
                JOIN client_onboarding.organization_users ou
                  ON ou.organization_id=our.organization_id
                 AND ou.id=our.organization_user_id
                JOIN client_onboarding.users u ON u.id=ou.user_id
                LEFT JOIN client_onboarding.notification_preferences np
                  ON np.organization_id=ou.organization_id
                 AND np.user_id=u.id
                 AND np.recipient_type='INTERNAL'
                WHERE our.organization_id=?
                  AND our.role_id=?
                  AND ou.status='ACTIVE'
                  AND u.status='ACTIVE'
                """,
                (rs, rowNumber) -> recipient(rs, "INTERNAL"),
                organizationId,
                roleId);
    }

    private List<Recipient> clientProjectRecipients(UUID organizationId, UUID projectId) {
        return jdbc.query(
                """
                SELECT DISTINCT u.id,
                                u.email,
                                u.display_name,
                                COALESCE(np.in_app_enabled, TRUE),
                                COALESCE(np.email_enabled, TRUE)
                FROM client_onboarding.client_user_projects cup
                JOIN client_onboarding.client_users cu
                  ON cu.organization_id=cup.organization_id
                 AND cu.id=cup.client_user_id
                JOIN client_onboarding.users u ON u.id=cu.user_id
                LEFT JOIN client_onboarding.notification_preferences np
                  ON np.organization_id=cu.organization_id
                 AND np.user_id=u.id
                 AND np.recipient_type='CLIENT'
                WHERE cup.organization_id=?
                  AND cup.project_id=?
                  AND cup.status='ACTIVE'
                  AND cu.status='ACTIVE'
                  AND u.status='ACTIVE'
                """,
                (rs, rowNumber) -> recipient(rs, "CLIENT"),
                organizationId,
                projectId);
    }

    private List<Recipient> internalProjectRecipients(UUID organizationId, UUID projectId) {
        return jdbc.query(
                """
                SELECT DISTINCT u.id,
                                u.email,
                                u.display_name,
                                COALESCE(np.in_app_enabled, TRUE),
                                COALESCE(np.email_enabled, TRUE)
                FROM client_onboarding.project_members pm
                JOIN client_onboarding.organization_users ou
                  ON ou.organization_id=pm.organization_id
                 AND ou.id=pm.organization_user_id
                JOIN client_onboarding.users u ON u.id=ou.user_id
                LEFT JOIN client_onboarding.notification_preferences np
                  ON np.organization_id=ou.organization_id
                 AND np.user_id=u.id
                 AND np.recipient_type='INTERNAL'
                WHERE pm.organization_id=?
                  AND pm.project_id=?
                  AND ou.status='ACTIVE'
                  AND u.status='ACTIVE'
                """,
                (rs, rowNumber) -> recipient(rs, "INTERNAL"),
                organizationId,
                projectId);
    }

    private List<Recipient> permissionRecipients(UUID organizationId, String permission) {
        if (permission == null || permission.isBlank()) return List.of();
        return jdbc.query(
                """
                SELECT DISTINCT u.id,
                                u.email,
                                u.display_name,
                                COALESCE(np.in_app_enabled, TRUE),
                                COALESCE(np.email_enabled, TRUE)
                FROM client_onboarding.organization_users ou
                JOIN client_onboarding.organization_user_roles our
                  ON our.organization_id=ou.organization_id
                 AND our.organization_user_id=ou.id
                JOIN client_onboarding.role_permissions rp
                  ON rp.organization_id=our.organization_id
                 AND rp.role_id=our.role_id
                JOIN client_onboarding.permissions p ON p.id=rp.permission_id
                JOIN client_onboarding.users u ON u.id=ou.user_id
                LEFT JOIN client_onboarding.notification_preferences np
                  ON np.organization_id=ou.organization_id
                 AND np.user_id=u.id
                 AND np.recipient_type='INTERNAL'
                WHERE ou.organization_id=?
                  AND ou.status='ACTIVE'
                  AND u.status='ACTIVE'
                  AND p.code=?
                """,
                (rs, rowNumber) -> recipient(rs, "INTERNAL"),
                organizationId,
                permission);
    }

    private List<Recipient> actor(UUID organizationId, JsonNode payload) {
        String raw = text(payload, "userId");
        if (raw == null) raw = text(payload, "actorId");
        if (raw == null) return List.of();

        UUID userId;
        try {
            userId = UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }

        List<Recipient> internal = jdbc.query(
                """
                SELECT u.id,
                       u.email,
                       u.display_name,
                       COALESCE(np.in_app_enabled, TRUE),
                       COALESCE(np.email_enabled, TRUE)
                FROM client_onboarding.organization_users ou
                JOIN client_onboarding.users u ON u.id=ou.user_id
                LEFT JOIN client_onboarding.notification_preferences np
                  ON np.organization_id=ou.organization_id
                 AND np.user_id=u.id
                 AND np.recipient_type='INTERNAL'
                WHERE ou.organization_id=?
                  AND ou.user_id=?
                  AND ou.status='ACTIVE'
                  AND u.status='ACTIVE'
                """,
                (rs, rowNumber) -> recipient(rs, "INTERNAL"),
                organizationId,
                userId);
        if (!internal.isEmpty()) return internal;

        return jdbc.query(
                """
                SELECT DISTINCT u.id,
                                u.email,
                                u.display_name,
                                COALESCE(np.in_app_enabled, TRUE),
                                COALESCE(np.email_enabled, TRUE)
                FROM client_onboarding.client_users cu
                JOIN client_onboarding.users u ON u.id=cu.user_id
                LEFT JOIN client_onboarding.notification_preferences np
                  ON np.organization_id=cu.organization_id
                 AND np.user_id=u.id
                 AND np.recipient_type='CLIENT'
                WHERE cu.organization_id=?
                  AND cu.user_id=?
                  AND cu.status='ACTIVE'
                  AND u.status='ACTIVE'
                """,
                (rs, rowNumber) -> recipient(rs, "CLIENT"),
                organizationId,
                userId);
    }

    private static Recipient recipient(java.sql.ResultSet rs, String type) throws java.sql.SQLException {
        return new Recipient(
                rs.getObject(1, UUID.class),
                type,
                rs.getString(2),
                rs.getString(3),
                rs.getBoolean(4),
                rs.getBoolean(5));
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record Recipient(
            UUID userId,
            String type,
            String email,
            String displayName,
            boolean inAppEnabled,
            boolean emailEnabled) {}
}
