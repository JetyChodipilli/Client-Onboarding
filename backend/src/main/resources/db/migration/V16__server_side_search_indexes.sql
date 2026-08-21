-- Phase 13 follow-up: bounded server-side prefix search for large tenant datasets.
-- text_pattern_ops keeps prefix LIKE predicates indexable even when the database locale
-- would otherwise prevent the normal btree operator class from being used for LIKE.

CREATE INDEX IF NOT EXISTS ix_clients_org_lower_name_pattern
    ON client_onboarding.clients (organization_id, (lower(name)) text_pattern_ops, id);

CREATE INDEX IF NOT EXISTS ix_services_org_lower_name_pattern
    ON client_onboarding.services (organization_id, (lower(name)) text_pattern_ops, id);

CREATE INDEX IF NOT EXISTS ix_services_org_lower_code_pattern
    ON client_onboarding.services (organization_id, (lower(code)) text_pattern_ops, id);

CREATE INDEX IF NOT EXISTS ix_projects_org_lower_name_pattern
    ON client_onboarding.projects (organization_id, (lower(name)) text_pattern_ops, id);
