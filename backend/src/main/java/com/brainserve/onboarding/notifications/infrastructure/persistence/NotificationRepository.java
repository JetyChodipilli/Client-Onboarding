package com.brainserve.onboarding.notifications.infrastructure.persistence;

import com.brainserve.onboarding.notifications.domain.model.Notification;
import com.brainserve.onboarding.notifications.domain.model.NotificationChannel;
import com.brainserve.onboarding.notifications.domain.model.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByOrganizationIdAndId(UUID org, UUID id);
    Optional<Notification> findByOrganizationIdAndIdAndRecipientUserId(UUID org, UUID id, UUID userId);

    @Query("""
            select n from Notification n
            where n.organizationId=:org and n.recipientUserId=:user
              and exists (
                select d.id from NotificationDelivery d
                where d.notificationId=n.id and d.channel=:channel and d.status<>:suppressed
              )
            """)
    Page<Notification> findVisible(@Param("org") UUID org, @Param("user") UUID user,
                                   @Param("channel") NotificationChannel channel,
                                   @Param("suppressed") NotificationDeliveryStatus suppressed,
                                   Pageable pageable);


    @Modifying
    @Query("""
            update Notification n set n.readAt=:now
            where n.organizationId=:org and n.recipientUserId=:user and n.readAt is null
              and exists (
                select d.id from NotificationDelivery d
                where d.notificationId=n.id and d.channel=:channel and d.status<>:suppressed
              )
            """)
    int markAllVisibleRead(@Param("org") UUID org, @Param("user") UUID user, @Param("now") Instant now,
                           @Param("channel") NotificationChannel channel,
                           @Param("suppressed") NotificationDeliveryStatus suppressed);

    @Query("""
            select count(n) from Notification n
            where n.organizationId=:org and n.recipientUserId=:user and n.readAt is null
              and exists (
                select d.id from NotificationDelivery d
                where d.notificationId=n.id and d.channel=:channel and d.status<>:suppressed
              )
            """)
    long countVisibleUnread(@Param("org") UUID org, @Param("user") UUID user,
                            @Param("channel") NotificationChannel channel,
                            @Param("suppressed") NotificationDeliveryStatus suppressed);
}
