package com.brainserve.clientonboarding.portal.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ClientInvitation(UUID id, UUID organizationId, UUID clientId, UUID contactId, UUID projectId,
                               UUID onboardingId, String invitedEmail, ClientRole clientRole,
                               String tokenHash, Status status, DeliveryStatus deliveryStatus,
                               Instant expiresAt, Instant sentAt, Instant acceptedAt, UUID acceptedBy,
                               Instant revokedAt, int resendCount, Instant createdAt, Instant updatedAt,
                               long version) {
    public enum ClientRole { ADMIN, MEMBER }
    public enum Status { PENDING, ACCEPTED, REVOKED }
    public enum DeliveryStatus { PENDING, SENT, FAILED }

    public boolean usableAt(Instant now) {
        return status == Status.PENDING && expiresAt.isAfter(now) && revokedAt == null && acceptedAt == null;
    }
}
