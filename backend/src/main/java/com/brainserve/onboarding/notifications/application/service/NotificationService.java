package com.brainserve.onboarding.notifications.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.AuthenticatedPrincipal;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.notifications.api.request.NotificationPreferencesRequest;
import com.brainserve.onboarding.notifications.api.response.NotificationPreferencesResponse;
import com.brainserve.onboarding.notifications.api.response.NotificationResponse;
import com.brainserve.onboarding.notifications.domain.model.Notification;
import com.brainserve.onboarding.notifications.domain.model.NotificationChannel;
import com.brainserve.onboarding.notifications.domain.model.NotificationDeliveryStatus;
import com.brainserve.onboarding.notifications.infrastructure.persistence.NotificationDeliveryRepository;
import com.brainserve.onboarding.notifications.infrastructure.persistence.NotificationRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final int MAX_PAGE = 100;

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notifications,
            NotificationDeliveryRepository deliveries,
            JdbcTemplate jdbc,
            Clock clock) {
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Creates a notification idempotently. The database unique key is the concurrency boundary, so this method
     * intentionally avoids a separate exists-before-insert query. This removes a round trip and avoids a
     * check-then-insert race under concurrent workers.
     */
    @Transactional
    public boolean createForUser(
            UUID organizationId,
            UUID userId,
            String recipientType,
            UUID templateId,
            String eventType,
            String sourceType,
            UUID sourceId,
            UUID projectId,
            String title,
            String body,
            String action,
            String dedupeKey,
            Collection<NotificationChannel> channels,
            boolean mandatory) {
        return createForRecipient(
                organizationId,
                userId,
                recipientType,
                null,
                null,
                templateId,
                eventType,
                sourceType,
                sourceId,
                projectId,
                title,
                body,
                action,
                dedupeKey,
                channels,
                mandatory);
    }

    /**
     * Worker-oriented variant that reuses an email address already loaded by the recipient resolver. This avoids
     * one users-table lookup per recipient in notification/reminder fan-out paths.
     */
    @Transactional
    public boolean createForResolvedRecipient(
            UUID organizationId,
            UUID userId,
            String recipientType,
            String resolvedEmail,
            boolean inAppEnabled,
            boolean emailEnabled,
            UUID templateId,
            String eventType,
            String sourceType,
            UUID sourceId,
            UUID projectId,
            String title,
            String body,
            String action,
            String dedupeKey,
            Collection<NotificationChannel> channels,
            boolean mandatory) {
        return createForRecipient(
                organizationId,
                userId,
                recipientType,
                resolvedEmail,
                new Prefs(inAppEnabled, emailEnabled),
                templateId,
                eventType,
                sourceType,
                sourceId,
                projectId,
                title,
                body,
                action,
                dedupeKey,
                channels,
                mandatory);
    }

    private boolean createForRecipient(
            UUID organizationId,
            UUID userId,
            String recipientType,
            String resolvedEmail,
            Prefs resolvedPrefs,
            UUID templateId,
            String eventType,
            String sourceType,
            UUID sourceId,
            UUID projectId,
            String title,
            String body,
            String action,
            String dedupeKey,
            Collection<NotificationChannel> channels,
            boolean mandatory) {
        LinkedHashSet<NotificationChannel> requestedChannels = new LinkedHashSet<>(channels);
        if (requestedChannels.isEmpty()) return false;

        Prefs prefs = resolvedPrefs != null ? resolvedPrefs : preference(organizationId, userId, recipientType);
        boolean needsEmail = requestedChannels.contains(NotificationChannel.EMAIL) && (mandatory || prefs.email());
        String emailAddress = needsEmail ? normalizedEmail(resolvedEmail, userId) : null;

        UUID notificationId = UUID.randomUUID();
        Instant now = clock.instant();
        int inserted = jdbc.update(
                """
                INSERT INTO client_onboarding.notifications
                  (id, organization_id, recipient_user_id, recipient_type, template_id, event_type,
                   source_type, source_id, project_id, title, body, action_url, dedupe_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, recipient_user_id, dedupe_key) DO NOTHING
                """,
                notificationId,
                organizationId,
                userId,
                recipientType,
                templateId,
                eventType,
                sourceType,
                sourceId,
                projectId,
                title,
                body,
                action,
                dedupeKey,
                Timestamp.from(now));
        if (inserted == 0) return false;

        List<Object[]> deliveryRows = new ArrayList<>(requestedChannels.size());
        for (NotificationChannel channel : requestedChannels) {
            boolean enabled = mandatory
                    || (channel == NotificationChannel.IN_APP ? prefs.inApp() : prefs.email());
            NotificationDeliveryStatus status;
            if (!enabled) status = NotificationDeliveryStatus.SUPPRESSED;
            else status = channel == NotificationChannel.IN_APP
                    ? NotificationDeliveryStatus.DELIVERED
                    : NotificationDeliveryStatus.QUEUED;

            deliveryRows.add(new Object[] {
                UUID.randomUUID(),
                organizationId,
                notificationId,
                channel.name(),
                channel == NotificationChannel.EMAIL ? emailAddress : null,
                status.name(),
                Timestamp.from(now),
                status == NotificationDeliveryStatus.DELIVERED ? Timestamp.from(now) : null,
                Timestamp.from(now),
                Timestamp.from(now)
            });
        }

        jdbc.batchUpdate(
                """
                INSERT INTO client_onboarding.notification_deliveries
                  (id, organization_id, notification_id, channel, recipient_address, status,
                   attempt_count, next_attempt_at, delivered_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                ON CONFLICT (notification_id, channel) DO NOTHING
                """,
                deliveryRows);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResult list(AuthenticatedPrincipal principal, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(MAX_PAGE, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Notification> result = notifications.findVisible(
                principal.organizationId(),
                principal.userId(),
                NotificationChannel.IN_APP,
                NotificationDeliveryStatus.SUPPRESSED,
                pageable);
        return new PageResult(
                result.getContent().stream().map(NotificationService::map).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                notifications.countVisibleUnread(
                        principal.organizationId(),
                        principal.userId(),
                        NotificationChannel.IN_APP,
                        NotificationDeliveryStatus.SUPPRESSED));
    }

    @Transactional
    public NotificationResponse read(AuthenticatedPrincipal principal, UUID id) {
        Notification notification = notifications
                .findByOrganizationIdAndIdAndRecipientUserId(principal.organizationId(), id, principal.userId())
                .orElseThrow(NotificationService::notFound);
        if (!deliveries.existsByNotificationIdAndChannelAndStatusNot(
                notification.getId(), NotificationChannel.IN_APP, NotificationDeliveryStatus.SUPPRESSED)) {
            throw notFound();
        }
        notification.markRead(clock.instant());
        notifications.saveAndFlush(notification);
        return map(notification);
    }

    @Transactional
    public int readAll(AuthenticatedPrincipal principal) {
        return notifications.markAllVisibleRead(
                principal.organizationId(),
                principal.userId(),
                clock.instant(),
                NotificationChannel.IN_APP,
                NotificationDeliveryStatus.SUPPRESSED);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse preferences(AuthenticatedPrincipal principal) {
        Prefs prefs = preference(
                principal.organizationId(),
                principal.userId(),
                principal instanceof ClientPrincipal ? "CLIENT" : "INTERNAL");
        return new NotificationPreferencesResponse(prefs.inApp(), prefs.email());
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(
            AuthenticatedPrincipal principal, NotificationPreferencesRequest request) {
        String type = principal instanceof ClientPrincipal ? "CLIENT" : "INTERNAL";
        jdbc.update(
                """
                INSERT INTO client_onboarding.notification_preferences
                  (organization_id, user_id, recipient_type, in_app_enabled, email_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, user_id, recipient_type)
                DO UPDATE SET in_app_enabled=EXCLUDED.in_app_enabled,
                              email_enabled=EXCLUDED.email_enabled,
                              updated_at=EXCLUDED.updated_at
                """,
                principal.organizationId(),
                principal.userId(),
                type,
                request.inAppEnabled(),
                request.emailEnabled(),
                Timestamp.from(clock.instant()));
        return new NotificationPreferencesResponse(request.inAppEnabled(), request.emailEnabled());
    }

    private Prefs preference(UUID organizationId, UUID userId, String recipientType) {
        List<Prefs> values = jdbc.query(
                """
                SELECT in_app_enabled, email_enabled
                FROM client_onboarding.notification_preferences
                WHERE organization_id=? AND user_id=? AND recipient_type=?
                """,
                (rs, rowNumber) -> new Prefs(rs.getBoolean(1), rs.getBoolean(2)),
                organizationId,
                userId,
                recipientType);
        return values.isEmpty() ? new Prefs(true, true) : values.getFirst();
    }

    private String normalizedEmail(String resolvedEmail, UUID userId) {
        if (resolvedEmail != null && !resolvedEmail.isBlank()) return resolvedEmail;
        return jdbc.queryForObject("SELECT email FROM client_onboarding.users WHERE id=?", String.class, userId);
    }

    private static NotificationResponse map(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventType(),
                notification.getSourceType(),
                notification.getSourceId(),
                notification.getProjectId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getActionUrl(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested notification was not found.");
    }

    private record Prefs(boolean inApp, boolean email) {}

    public record PageResult(
            List<NotificationResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long unreadCount) {}
}
