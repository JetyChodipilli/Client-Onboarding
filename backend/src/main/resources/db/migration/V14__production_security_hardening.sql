-- Phase 13: production hardening discovered during the accumulated Phase 0-12 audit.
-- Never rewrite earlier applied migrations; strengthen transition guards forward-only.

CREATE OR REPLACE FUNCTION client_onboarding.validate_project_readiness_activation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'READY' AND OLD.status IS DISTINCT FROM 'READY' THEN
        IF OLD.status <> 'ONBOARDING' THEN
            RAISE EXCEPTION 'project can become READY only from ONBOARDING';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM client_onboarding.onboarding_instances oi
            WHERE oi.organization_id = NEW.organization_id
              AND oi.project_id = NEW.id
              AND oi.status = 'COMPLETED'
        ) THEN
            RAISE EXCEPTION 'project cannot become READY before onboarding is completed';
        END IF;
    END IF;

    IF NEW.status = 'ACTIVE' AND OLD.status IS DISTINCT FROM 'ACTIVE' THEN
        IF NOT (
            OLD.status = 'READY'
            OR (OLD.status = 'ON_HOLD' AND OLD.hold_from_status = 'ACTIVE')
        ) THEN
            RAISE EXCEPTION 'project can become ACTIVE only from READY or resume from ACTIVE hold';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- Common incident-response and backlog query paths.
CREATE INDEX IF NOT EXISTS ix_outbox_failure_observability
    ON client_onboarding.outbox_events (processing_status, attempt_count, available_at, occurred_at, id)
    WHERE processing_status IN ('PENDING','FAILED','PROCESSING');
CREATE INDEX IF NOT EXISTS ix_notification_delivery_failure_observability
    ON client_onboarding.notification_deliveries (status, attempt_count, next_attempt_at, updated_at, id)
    WHERE status IN ('QUEUED','PROCESSING','FAILED','DEAD');
CREATE INDEX IF NOT EXISTS ix_webhook_failure_observability
    ON client_onboarding.webhook_events (processing_status, attempt_count, received_at, id)
    WHERE processing_status IN ('RECEIVED','FAILED');
