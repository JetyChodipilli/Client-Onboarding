-- Phase 3: workflow templates, immutable published versions, workflow graph configuration,
-- onboarding snapshots/instances, step instances, dependency snapshots, and transactional outbox.
-- Also hardens the Phase 2 activity actor relationship without rewriting an applied migration.

-- Phase 2 hardening: activity actors, when present, must belong to the same organization.
ALTER TABLE client_onboarding.activity_logs
    DROP CONSTRAINT IF EXISTS activity_logs_actor_user_id_fkey;
ALTER TABLE client_onboarding.activity_logs
    ADD CONSTRAINT fk_activity_logs_actor_tenant
        FOREIGN KEY (organization_id, actor_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id);

-- Phase 1 hardening: audit actors must also be members of the tenant they are recorded against.
ALTER TABLE client_onboarding.audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_actor_user_id_fkey;
ALTER TABLE client_onboarding.audit_logs
    ADD CONSTRAINT fk_audit_logs_actor_tenant
        FOREIGN KEY (organization_id, actor_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id);

INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000028', 'WORKFLOW_READ', 'WORKFLOW', 'Read onboarding workflow templates and versions.'),
('00000000-0000-0000-0000-000000000029', 'WORKFLOW_MANAGE', 'WORKFLOW', 'Create, edit, version, publish, and archive onboarding workflows.'),
('00000000-0000-0000-0000-000000000030', 'ONBOARDING_READ', 'ONBOARDING', 'Read onboarding instances and workflow progress.');

INSERT INTO client_onboarding.role_permissions
    (organization_id, role_id, permission_id, created_by)
SELECT r.organization_id, r.id, p.id, r.created_by
FROM client_onboarding.roles r
CROSS JOIN client_onboarding.permissions p
WHERE r.system_role = TRUE
  AND r.code = 'ORGANIZATION_ADMIN'
  AND r.status = 'ACTIVE'
  AND p.code IN ('WORKFLOW_READ', 'WORKFLOW_MANAGE', 'ONBOARDING_READ')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE client_onboarding.onboarding_templates (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_onboarding_templates_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_onboarding_templates_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_onboarding_templates_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE UNIQUE INDEX ux_onboarding_templates_active_name_lower
    ON client_onboarding.onboarding_templates (organization_id, LOWER(name))
    WHERE status = 'ACTIVE';
CREATE INDEX ix_onboarding_templates_org_status_updated
    ON client_onboarding.onboarding_templates (organization_id, status, updated_at DESC, id DESC);

CREATE TABLE client_onboarding.onboarding_template_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    template_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    change_note VARCHAR(500) NULL,
    published_at TIMESTAMPTZ NULL,
    published_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_onboarding_template_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_onboarding_template_versions_org_id_template UNIQUE (organization_id, id, template_id),
    CONSTRAINT ux_onboarding_template_versions_number UNIQUE (organization_id, template_id, version_number),
    CONSTRAINT fk_onboarding_template_versions_template_tenant
        FOREIGN KEY (organization_id, template_id)
        REFERENCES client_onboarding.onboarding_templates(organization_id, id),
    CONSTRAINT fk_onboarding_template_versions_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_onboarding_template_versions_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_onboarding_template_versions_publisher_tenant
        FOREIGN KEY (organization_id, published_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_onboarding_template_versions_publish_fields CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND published_by IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND published_by IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_onboarding_template_versions_one_draft
    ON client_onboarding.onboarding_template_versions (organization_id, template_id)
    WHERE status = 'DRAFT';
CREATE INDEX ix_onboarding_template_versions_template
    ON client_onboarding.onboarding_template_versions (organization_id, template_id, version_number DESC);

CREATE TABLE client_onboarding.onboarding_template_steps (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    template_version_id UUID NOT NULL,
    step_key VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NULL,
    step_type VARCHAR(32) NOT NULL
        CHECK (step_type IN ('WELCOME','INSTRUCTION','FORM','FILE_UPLOAD','PAYMENT','CONTRACT','PLATFORM_ACCESS','MANUAL_TASK','APPROVAL','EXTERNAL_LINK','VIDEO_GUIDE','MEETING','CUSTOM')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    blocking BOOLEAN NOT NULL DEFAULT TRUE,
    client_visible BOOLEAN NOT NULL DEFAULT TRUE,
    requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    dependency_mode VARCHAR(8) NOT NULL DEFAULT 'NONE'
        CHECK (dependency_mode IN ('NONE','ALL','ANY')),
    condition_expression JSONB NOT NULL DEFAULT '{"op":"ALWAYS"}'::jsonb,
    assigned_role_id UUID NULL,
    due_after_hours INTEGER NULL CHECK (due_after_hours IS NULL OR (due_after_hours >= 0 AND due_after_hours <= 8760)),
    reminder_policy_id UUID NULL,
    allow_skip BOOLEAN NOT NULL DEFAULT FALSE,
    allow_reopen BOOLEAN NOT NULL DEFAULT FALSE,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_onboarding_template_steps_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_onboarding_template_steps_version_id UNIQUE (organization_id, template_version_id, id),
    CONSTRAINT ux_onboarding_template_steps_key UNIQUE (organization_id, template_version_id, step_key),
    CONSTRAINT ux_onboarding_template_steps_order UNIQUE (organization_id, template_version_id, display_order),
    CONSTRAINT fk_onboarding_template_steps_version_tenant
        FOREIGN KEY (organization_id, template_version_id)
        REFERENCES client_onboarding.onboarding_template_versions(organization_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_template_steps_role_tenant
        FOREIGN KEY (organization_id, assigned_role_id)
        REFERENCES client_onboarding.roles(organization_id, id),
    CONSTRAINT fk_onboarding_template_steps_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_onboarding_template_steps_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_onboarding_template_steps_json_object CHECK (
        jsonb_typeof(condition_expression) = 'object' AND jsonb_typeof(configuration_json) = 'object'
    )
);
CREATE INDEX ix_onboarding_template_steps_version_order
    ON client_onboarding.onboarding_template_steps (organization_id, template_version_id, display_order, id);

CREATE TABLE client_onboarding.onboarding_step_dependencies (
    organization_id UUID NOT NULL,
    template_version_id UUID NOT NULL,
    step_id UUID NOT NULL,
    depends_on_step_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    PRIMARY KEY (step_id, depends_on_step_id),
    CONSTRAINT fk_onboarding_step_dependencies_step_tenant_version
        FOREIGN KEY (organization_id, template_version_id, step_id)
        REFERENCES client_onboarding.onboarding_template_steps(organization_id, template_version_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_step_dependencies_dependency_tenant_version
        FOREIGN KEY (organization_id, template_version_id, depends_on_step_id)
        REFERENCES client_onboarding.onboarding_template_steps(organization_id, template_version_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_step_dependencies_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_onboarding_step_dependencies_not_self CHECK (step_id <> depends_on_step_id)
);
CREATE INDEX ix_onboarding_step_dependencies_version_step
    ON client_onboarding.onboarding_step_dependencies (organization_id, template_version_id, step_id);

-- Published workflow versions and their graph definitions are immutable at the database boundary.
CREATE OR REPLACE FUNCTION client_onboarding.prevent_published_workflow_version_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'published workflow versions are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_onboarding_template_versions_immutable
BEFORE UPDATE OR DELETE ON client_onboarding.onboarding_template_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.prevent_published_workflow_version_mutation();

CREATE OR REPLACE FUNCTION client_onboarding.prevent_published_workflow_definition_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_version_id UUID;
    v_organization_id UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_version_id := OLD.template_version_id;
        v_organization_id := OLD.organization_id;
    ELSE
        v_version_id := NEW.template_version_id;
        v_organization_id := NEW.organization_id;
    END IF;
    IF EXISTS (
        SELECT 1
        FROM client_onboarding.onboarding_template_versions v
        WHERE v.organization_id = v_organization_id
          AND v.id = v_version_id
          AND v.status = 'PUBLISHED'
    ) THEN
        RAISE EXCEPTION 'published workflow definitions are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_onboarding_template_steps_published_immutable
BEFORE INSERT OR UPDATE OR DELETE ON client_onboarding.onboarding_template_steps
FOR EACH ROW EXECUTE FUNCTION client_onboarding.prevent_published_workflow_definition_mutation();

CREATE TRIGGER trg_onboarding_step_dependencies_published_immutable
BEFORE INSERT OR UPDATE OR DELETE ON client_onboarding.onboarding_step_dependencies
FOR EACH ROW EXECUTE FUNCTION client_onboarding.prevent_published_workflow_definition_mutation();

CREATE TABLE client_onboarding.onboarding_instances (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    template_id UUID NOT NULL,
    template_version_id UUID NOT NULL,
    template_name_snapshot VARCHAR(180) NOT NULL,
    template_version_number INTEGER NOT NULL CHECK (template_version_number > 0),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','INVITED','IN_PROGRESS','AWAITING_INTERNAL_REVIEW','NEEDS_REVISION','APPROVED','COMPLETED','PAUSED','EXPIRED','CANCELLED')),
    paused_from_status VARCHAR(40) NULL
        CHECK (paused_from_status IS NULL OR paused_from_status IN ('INVITED','IN_PROGRESS','AWAITING_INTERNAL_REVIEW','NEEDS_REVISION')),
    ready BOOLEAN NOT NULL DEFAULT FALSE,
    start_idempotency_key_hash CHAR(64) NOT NULL,
    start_request_hash CHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_onboarding_instances_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_onboarding_instances_org_project UNIQUE (organization_id, project_id),
    CONSTRAINT ux_onboarding_instances_org_id_project UNIQUE (organization_id, id, project_id),
    CONSTRAINT ux_onboarding_instances_org_id_version UNIQUE (organization_id, id, template_version_id),
    CONSTRAINT ux_onboarding_instances_idempotency UNIQUE (organization_id, start_idempotency_key_hash),
    CONSTRAINT fk_onboarding_instances_project_tenant
        FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id),
    CONSTRAINT fk_onboarding_instances_template_tenant
        FOREIGN KEY (organization_id, template_id)
        REFERENCES client_onboarding.onboarding_templates(organization_id, id),
    CONSTRAINT fk_onboarding_instances_version_tenant_template
        FOREIGN KEY (organization_id, template_version_id, template_id)
        REFERENCES client_onboarding.onboarding_template_versions(organization_id, id, template_id),
    CONSTRAINT fk_onboarding_instances_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_onboarding_instances_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_onboarding_instances_org_status_started
    ON client_onboarding.onboarding_instances (organization_id, status, started_at DESC, id DESC);
CREATE INDEX ix_onboarding_instances_template_version
    ON client_onboarding.onboarding_instances (organization_id, template_version_id, started_at DESC);

CREATE TABLE client_onboarding.onboarding_step_instances (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    template_version_id UUID NOT NULL,
    source_template_step_id UUID NOT NULL,
    step_key VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NULL,
    step_type VARCHAR(32) NOT NULL
        CHECK (step_type IN ('WELCOME','INSTRUCTION','FORM','FILE_UPLOAD','PAYMENT','CONTRACT','PLATFORM_ACCESS','MANUAL_TASK','APPROVAL','EXTERNAL_LINK','VIDEO_GUIDE','MEETING','CUSTOM')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    required BOOLEAN NOT NULL,
    blocking BOOLEAN NOT NULL,
    client_visible BOOLEAN NOT NULL,
    requires_review BOOLEAN NOT NULL,
    dependency_mode VARCHAR(8) NOT NULL CHECK (dependency_mode IN ('NONE','ALL','ANY')),
    condition_expression JSONB NOT NULL,
    assigned_role_id UUID NULL,
    due_at TIMESTAMPTZ NULL,
    reminder_policy_id UUID NULL,
    allow_skip BOOLEAN NOT NULL,
    allow_reopen BOOLEAN NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL
        CHECK (status IN ('LOCKED','AVAILABLE','IN_PROGRESS','SUBMITTED','UNDER_REVIEW','NEEDS_REVISION','COMPLETED','SKIPPED','FAILED','CANCELLED')),
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_onboarding_step_instances_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_onboarding_step_instances_onboarding_id UNIQUE (organization_id, onboarding_id, id),
    CONSTRAINT ux_onboarding_step_instances_key UNIQUE (organization_id, onboarding_id, step_key),
    CONSTRAINT ux_onboarding_step_instances_order UNIQUE (organization_id, onboarding_id, display_order),
    CONSTRAINT fk_onboarding_step_instances_onboarding_version_tenant
        FOREIGN KEY (organization_id, onboarding_id, template_version_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, template_version_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_step_instances_source_version_tenant
        FOREIGN KEY (organization_id, template_version_id, source_template_step_id)
        REFERENCES client_onboarding.onboarding_template_steps(organization_id, template_version_id, id),
    CONSTRAINT fk_onboarding_step_instances_role_tenant
        FOREIGN KEY (organization_id, assigned_role_id)
        REFERENCES client_onboarding.roles(organization_id, id),
    CONSTRAINT fk_onboarding_step_instances_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_onboarding_step_instances_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_onboarding_step_instances_json_object CHECK (
        jsonb_typeof(condition_expression) = 'object' AND jsonb_typeof(configuration_json) = 'object'
    )
);
CREATE INDEX ix_onboarding_step_instances_onboarding_status_order
    ON client_onboarding.onboarding_step_instances (organization_id, onboarding_id, status, display_order, id);
CREATE INDEX ix_onboarding_step_instances_due
    ON client_onboarding.onboarding_step_instances (organization_id, due_at, status)
    WHERE due_at IS NOT NULL AND status NOT IN ('COMPLETED','SKIPPED','CANCELLED');

CREATE TABLE client_onboarding.onboarding_step_instance_dependencies (
    organization_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    step_instance_id UUID NOT NULL,
    depends_on_step_instance_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (step_instance_id, depends_on_step_instance_id),
    CONSTRAINT fk_onboarding_instance_dependencies_step_tenant_instance
        FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_instance_dependencies_dependency_tenant_instance
        FOREIGN KEY (organization_id, onboarding_id, depends_on_step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_onboarding_instance_dependencies_not_self CHECK (step_instance_id <> depends_on_step_instance_id)
);
CREATE INDEX ix_onboarding_instance_dependencies_step
    ON client_onboarding.onboarding_step_instance_dependencies (organization_id, onboarding_id, step_instance_id);

CREATE TABLE client_onboarding.outbox_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload_version INTEGER NOT NULL DEFAULT 1 CHECK (payload_version > 0),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processing_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ NULL,
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_outbox_events_payload_object CHECK (jsonb_typeof(payload) = 'object')
);
CREATE INDEX ix_outbox_events_pending
    ON client_onboarding.outbox_events (processing_status, available_at, occurred_at, id)
    WHERE processing_status IN ('PENDING','FAILED');
CREATE INDEX ix_outbox_events_aggregate
    ON client_onboarding.outbox_events (organization_id, aggregate_type, aggregate_id, occurred_at DESC);
