package com.brainserve.onboarding.integrations.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Controlled owner for the shared webhook_events idempotency ledger.
 * Provider adapters verify authenticity before this service is called.
 */
@Service
public class WebhookEventLedgerService {
    private final JdbcTemplate jdbc;

    public WebhookEventLedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Claim claim(UUID organizationId, String provider, String eventId, String eventType,
                       String payloadHash, Instant receivedAt) {
        try {
            int inserted = jdbc.update("""
                    INSERT INTO client_onboarding.webhook_events
                        (id, organization_id, provider, provider_event_id, event_type, payload_hash,
                         received_at, processing_status, attempt_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'RECEIVED', 1)
                    ON CONFLICT (provider, provider_event_id) DO NOTHING
                    """, UUID.randomUUID(), organizationId, provider, eventId, eventType, payloadHash, receivedAt);
            Existing existing = find(provider, eventId);
            if (existing == null) throw new IllegalStateException("Webhook ledger claim could not be read back");
            if (!existing.payloadHash().equals(payloadHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "WEBHOOK_EVENT_COLLISION",
                        "Provider event id was reused with different content.");
            }
            if (!existing.organizationId().equals(organizationId)) {
                throw new ApiException(HttpStatus.CONFLICT, "WEBHOOK_EVENT_COLLISION",
                        "Provider event id was reused for another organization.");
            }
            if (inserted == 0 && ("FAILED".equals(existing.status()) || "RECEIVED".equals(existing.status()))) {
                jdbc.update("""
                        UPDATE client_onboarding.webhook_events
                        SET processing_status='RECEIVED', attempt_count=attempt_count+1, last_error=NULL
                        WHERE provider=? AND provider_event_id=?
                        """, provider, eventId);
                return new Claim(true, true, "RECEIVED");
            }
            return new Claim(inserted == 1, false, existing.status());
        } catch (DataIntegrityViolationException ex) {
            Existing existing = find(provider, eventId);
            if (existing != null && existing.payloadHash().equals(payloadHash)
                    && existing.organizationId().equals(organizationId)) {
                return new Claim(false, false, existing.status());
            }
            throw ex;
        }
    }

    public void processed(String provider, String eventId, Instant at) {
        update(provider, eventId, "PROCESSED", null, at);
    }

    public void ignored(String provider, String eventId, Instant at) {
        update(provider, eventId, "IGNORED", null, at);
    }

    public void failed(String provider, String eventId, String error, Instant at) {
        String safe = error == null ? "Webhook processing failed" : error.substring(0, Math.min(error.length(), 2000));
        update(provider, eventId, "FAILED", safe, at);
    }

    private void update(String provider, String eventId, String status, String error, Instant at) {
        int count = jdbc.update("""
                UPDATE client_onboarding.webhook_events
                SET processing_status=?, processed_at=?, last_error=?
                WHERE provider=? AND provider_event_id=?
                """, status, at, error, provider, eventId);
        if (count != 1) throw new IllegalStateException("Webhook ledger row disappeared during processing");
    }

    private Existing find(String provider, String eventId) {
        return jdbc.query("""
                SELECT organization_id, payload_hash, processing_status
                FROM client_onboarding.webhook_events
                WHERE provider=? AND provider_event_id=?
                """, rs -> rs.next() ? new Existing(
                rs.getObject("organization_id", UUID.class), rs.getString("payload_hash"),
                rs.getString("processing_status")) : null, provider, eventId);
    }

    public record Claim(boolean process, boolean retry, String existingStatus) {}
    private record Existing(UUID organizationId, String payloadHash, String status) {}
}
