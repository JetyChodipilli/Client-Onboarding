package com.brainserve.onboarding.notifications.api.response;
import java.time.Instant;import java.util.UUID;public record NotificationResponse(UUID id,String eventType,String sourceType,UUID sourceId,UUID projectId,String title,String body,String actionUrl,Instant readAt,Instant createdAt){}
