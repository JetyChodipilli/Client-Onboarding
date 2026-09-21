INSERT INTO permissions (id, code, description, created_at) VALUES
    ('00000000-0000-0000-0000-000000000025', 'WORKFLOW_READ',
     'Read workflow templates and onboarding instances', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000026', 'WORKFLOW_MANAGE',
     'Create and publish workflow templates', CURRENT_TIMESTAMP);

CREATE TABLE onboarding_templates (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    service_id uuid,
    name varchar(180) NOT NULL,
    description varchar(1000),
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, name),
    FOREIGN KEY (organization_id, service_id) REFERENCES services(organization_id, id)
);

CREATE TABLE onboarding_template_versions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    template_id uuid NOT NULL,
    version_number integer NOT NULL CHECK (version_number > 0),
    status varchar(24) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at timestamptz,
    published_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, template_id, id),
    UNIQUE (organization_id, template_id, version_number),
    FOREIGN KEY (organization_id, template_id) REFERENCES onboarding_templates(organization_id, id)
);

CREATE TABLE onboarding_template_steps (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    template_version_id uuid NOT NULL,
    step_key varchar(80) NOT NULL,
    name varchar(180) NOT NULL,
    description varchar(1000),
    step_type varchar(40) NOT NULL CHECK (step_type IN
        ('WELCOME', 'INSTRUCTION', 'FORM', 'FILE_UPLOAD', 'PAYMENT', 'CONTRACT', 'PLATFORM_ACCESS',
         'MANUAL_TASK', 'APPROVAL', 'EXTERNAL_LINK', 'VIDEO_GUIDE', 'MEETING', 'CUSTOM')),
    display_order integer NOT NULL CHECK (display_order >= 0),
    required boolean NOT NULL,
    blocking boolean NOT NULL,
    client_visible boolean NOT NULL,
    requires_review boolean NOT NULL,
    dependency_mode varchar(8) NOT NULL CHECK (dependency_mode IN ('NONE', 'ALL', 'ANY')),
    condition_expression text,
    assigned_role varchar(100),
    due_after_hours integer CHECK (due_after_hours IS NULL OR due_after_hours >= 0),
    reminder_policy_id uuid,
    allow_skip boolean NOT NULL,
    allow_reopen boolean NOT NULL,
    configuration_json text NOT NULL,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, template_version_id, id),
    UNIQUE (organization_id, template_version_id, step_key),
    UNIQUE (organization_id, template_version_id, display_order),
    FOREIGN KEY (organization_id, template_version_id)
        REFERENCES onboarding_template_versions(organization_id, id)
);

CREATE TABLE onboarding_step_dependencies (
    organization_id uuid NOT NULL,
    template_version_id uuid NOT NULL,
    step_id uuid NOT NULL,
    depends_on_step_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    PRIMARY KEY (organization_id, step_id, depends_on_step_id),
    CHECK (step_id <> depends_on_step_id),
    FOREIGN KEY (organization_id, template_version_id)
        REFERENCES onboarding_template_versions(organization_id, id),
    FOREIGN KEY (organization_id, template_version_id, step_id)
        REFERENCES onboarding_template_steps(organization_id, template_version_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, template_version_id, depends_on_step_id)
        REFERENCES onboarding_template_steps(organization_id, template_version_id, id)
);

CREATE TABLE onboarding_instances (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    project_id uuid NOT NULL,
    source_template_id uuid NOT NULL,
    source_template_version_id uuid NOT NULL,
    snapshot_version_number integer NOT NULL,
    snapshot_json text NOT NULL,
    status varchar(40) NOT NULL CHECK (status IN
        ('DRAFT', 'INVITED', 'IN_PROGRESS', 'AWAITING_INTERNAL_REVIEW', 'NEEDS_REVISION',
         'APPROVED', 'COMPLETED', 'PAUSED', 'EXPIRED', 'CANCELLED')),
    ready boolean NOT NULL DEFAULT FALSE,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, project_id),
    FOREIGN KEY (organization_id, project_id) REFERENCES projects(organization_id, id),
    FOREIGN KEY (organization_id, source_template_id) REFERENCES onboarding_templates(organization_id, id),
    FOREIGN KEY (organization_id, source_template_id, source_template_version_id)
        REFERENCES onboarding_template_versions(organization_id, template_id, id)
);

CREATE TABLE onboarding_step_instances (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    onboarding_id uuid NOT NULL,
    source_step_id uuid NOT NULL,
    step_key varchar(80) NOT NULL,
    name varchar(180) NOT NULL,
    description varchar(1000),
    step_type varchar(40) NOT NULL,
    display_order integer NOT NULL,
    required boolean NOT NULL,
    blocking boolean NOT NULL,
    client_visible boolean NOT NULL,
    requires_review boolean NOT NULL,
    dependency_mode varchar(8) NOT NULL,
    condition_expression text,
    assigned_role varchar(100),
    due_at timestamptz,
    allow_skip boolean NOT NULL,
    allow_reopen boolean NOT NULL,
    configuration_json text NOT NULL,
    applicable boolean NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN
        ('LOCKED', 'AVAILABLE', 'IN_PROGRESS', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_REVISION',
         'COMPLETED', 'SKIPPED', 'FAILED', 'CANCELLED')),
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, onboarding_id, id),
    UNIQUE (organization_id, onboarding_id, step_key),
    FOREIGN KEY (organization_id, onboarding_id) REFERENCES onboarding_instances(organization_id, id),
    FOREIGN KEY (organization_id, source_step_id) REFERENCES onboarding_template_steps(organization_id, id)
);

CREATE TABLE onboarding_step_instance_dependencies (
    organization_id uuid NOT NULL,
    onboarding_id uuid NOT NULL,
    step_instance_id uuid NOT NULL,
    depends_on_step_instance_id uuid NOT NULL,
    PRIMARY KEY (organization_id, step_instance_id, depends_on_step_instance_id),
    CHECK (step_instance_id <> depends_on_step_instance_id),
    FOREIGN KEY (organization_id, onboarding_id) REFERENCES onboarding_instances(organization_id, id),
    FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES onboarding_step_instances(organization_id, onboarding_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, onboarding_id, depends_on_step_instance_id)
        REFERENCES onboarding_step_instances(organization_id, onboarding_id, id)
);

CREATE TABLE command_idempotency (
    organization_id uuid NOT NULL,
    command_scope varchar(80) NOT NULL,
    idempotency_key varchar(120) NOT NULL,
    resource_id uuid NOT NULL,
    request_fingerprint char(64) NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, command_scope, idempotency_key)
);

CREATE INDEX idx_templates_org_status ON onboarding_templates (organization_id, status, updated_at DESC);
CREATE INDEX idx_template_versions_template ON onboarding_template_versions
    (organization_id, template_id, version_number DESC);
CREATE INDEX idx_template_steps_version_order ON onboarding_template_steps
    (organization_id, template_version_id, display_order);
CREATE INDEX idx_onboarding_project_status ON onboarding_instances (organization_id, project_id, status);
CREATE INDEX idx_onboarding_status_updated ON onboarding_instances (organization_id, status, updated_at DESC);
CREATE INDEX idx_step_instances_onboarding_order ON onboarding_step_instances
    (organization_id, onboarding_id, display_order);
CREATE INDEX idx_step_instances_status_due ON onboarding_step_instances (organization_id, status, due_at);
