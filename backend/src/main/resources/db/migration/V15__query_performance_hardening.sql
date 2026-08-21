-- Phase 13 hardening: indexes added only for concrete query paths observed in the application.
-- Keep tenant-scoped OLTP reads tenant-prefixed. Worker indexes are intentionally global where
-- background jobs scan across tenants. These are performance aids only; PostgreSQL remains the
-- authoritative source of truth and no mutable security/workflow state is cached.

-- Notification feed: the existing unread index places read_at before the feed ordering columns,
-- which is ideal for unread predicates but not for the normal newest-first inbox query.
CREATE INDEX IF NOT EXISTS ix_notifications_recipient_feed
    ON client_onboarding.notifications (organization_id, recipient_user_id, created_at DESC, id DESC);

-- Tenant administration screens use these exact orderings.
CREATE INDEX IF NOT EXISTS ix_notification_templates_org_name
    ON client_onboarding.notification_templates (organization_id, name, id);

CREATE INDEX IF NOT EXISTS ix_notification_deliveries_org_created
    ON client_onboarding.notification_deliveries (organization_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_scheduled_reminders_org_next_run
    ON client_onboarding.scheduled_reminders (organization_id, next_run_at, id);

CREATE INDEX IF NOT EXISTS ix_onboarding_instances_org_started
    ON client_onboarding.onboarding_instances (organization_id, started_at DESC, id DESC);

-- Task lists are ordered by due date and recency. Separate selective indexes avoid forcing
-- PostgreSQL to sort the most common all/mine/project views after tenant filtering.
CREATE INDEX IF NOT EXISTS ix_tasks_org_due_created
    ON client_onboarding.tasks (organization_id, due_at ASC NULLS LAST, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_tasks_org_assignee_type_due_created
    ON client_onboarding.tasks (organization_id, assigned_user_id, assigned_user_type,
                                due_at ASC NULLS LAST, created_at DESC, id DESC)
    WHERE assigned_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_tasks_org_project_due_created
    ON client_onboarding.tasks (organization_id, project_id,
                                due_at ASC NULLS LAST, created_at DESC, id DESC)
    WHERE project_id IS NOT NULL;

-- The overdue worker is cross-tenant by design. The older tenant-prefixed due-date index cannot
-- efficiently drive this global ordered scan, so use a narrow partial index for eligible states.
CREATE INDEX IF NOT EXISTS ix_invoices_overdue_worker
    ON client_onboarding.invoices (due_at, id)
    WHERE due_at IS NOT NULL AND status IN ('SENT', 'VIEWED');

-- Notification routing consumes the outbox in occurrence order independently of the generic
-- publisher state. This index prevents repeated full-table sorts as the outbox grows.
CREATE INDEX IF NOT EXISTS ix_outbox_events_notification_order
    ON client_onboarding.outbox_events (occurred_at, id);

-- Recovery/retry paths are global worker scans; keep the index partial so successful receipts do
-- not bloat it.
CREATE INDEX IF NOT EXISTS ix_notification_event_receipts_retry
    ON client_onboarding.notification_event_receipts (status, next_attempt_at, outbox_event_id)
    WHERE status IN ('PROCESSING', 'FAILED');
