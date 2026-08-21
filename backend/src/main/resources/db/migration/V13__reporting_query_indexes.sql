-- Phase 12: tenant-scoped reporting remains a read model; add only query-path indexes, not duplicate aggregate tables.
CREATE INDEX IF NOT EXISTS ix_onboarding_instances_reporting
    ON client_onboarding.onboarding_instances (organization_id, status, completed_at, started_at, project_id);
CREATE INDEX IF NOT EXISTS ix_onboarding_steps_reporting
    ON client_onboarding.onboarding_step_instances (organization_id, step_type, status, onboarding_id, client_visible, due_at);
CREATE INDEX IF NOT EXISTS ix_invoices_reporting
    ON client_onboarding.invoices (organization_id, status, sent_at, paid_at, project_id);
CREATE INDEX IF NOT EXISTS ix_contracts_reporting
    ON client_onboarding.contracts (organization_id, status, sent_at, signed_at, project_id);
CREATE INDEX IF NOT EXISTS ix_access_reporting
    ON client_onboarding.platform_access_requests (organization_id, status, project_id);
CREATE INDEX IF NOT EXISTS ix_assets_reporting
    ON client_onboarding.assets (organization_id, status, project_id, requirement_id);
