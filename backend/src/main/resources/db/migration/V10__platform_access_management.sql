-- Phase 9: configurable/versioned platform-access guides, client submissions,
-- internal verification/revision, workflow integration, and credential-safe data boundaries.

INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000035', 'ACCESS_READ', 'ACCESS', 'Read platform access guides, requests, submissions, and verification history.'),
('00000000-0000-0000-0000-000000000036', 'ACCESS_MANAGE', 'ACCESS', 'Create, version, publish, and archive platform access guides.');

INSERT INTO client_onboarding.role_permissions (organization_id, role_id, permission_id, created_by)
SELECT r.organization_id, r.id, p.id, r.created_by
FROM client_onboarding.roles r
CROSS JOIN client_onboarding.permissions p
WHERE r.system_role = TRUE
  AND r.code = 'ORGANIZATION_ADMIN'
  AND r.status = 'ACTIVE'
  AND p.code IN ('ACCESS_READ','ACCESS_MANAGE','ACCESS_VERIFY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE client_onboarding.platform_access_types (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_platform_access_types_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_platform_access_types_org_code UNIQUE (organization_id, code),
    CONSTRAINT fk_platform_access_types_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_platform_access_types_updater FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_platform_access_types_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_]{1,79}$'),
    CONSTRAINT ck_platform_access_types_archive CHECK (
        (status='ARCHIVED' AND archived_at IS NOT NULL) OR (status='ACTIVE' AND archived_at IS NULL)
    )
);
CREATE INDEX ix_platform_access_types_org_status
    ON client_onboarding.platform_access_types (organization_id, status, updated_at DESC, id DESC);

CREATE TABLE client_onboarding.platform_access_type_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    access_type_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    change_note VARCHAR(500) NULL,
    instructions_markdown TEXT NOT NULL,
    help_url VARCHAR(2000) NULL,
    resources_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    published_at TIMESTAMPTZ NULL,
    published_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_platform_access_type_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_platform_access_type_versions_org_id_type UNIQUE (organization_id, id, access_type_id),
    CONSTRAINT ux_platform_access_type_versions_number UNIQUE (organization_id, access_type_id, version_number),
    CONSTRAINT fk_platform_access_type_versions_type FOREIGN KEY (organization_id, access_type_id)
        REFERENCES client_onboarding.platform_access_types(organization_id, id),
    CONSTRAINT fk_platform_access_type_versions_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_platform_access_type_versions_updater FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_platform_access_type_versions_publisher FOREIGN KEY (organization_id, published_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_platform_access_type_versions_publish CHECK (
        (status='PUBLISHED' AND published_at IS NOT NULL AND published_by IS NOT NULL)
        OR (status='DRAFT' AND published_at IS NULL AND published_by IS NULL)
    ),
    CONSTRAINT ck_platform_access_type_versions_resources CHECK (
        jsonb_typeof(resources_json)='array' AND jsonb_array_length(resources_json) <= 12
    )
);
CREATE UNIQUE INDEX ux_platform_access_type_versions_one_draft
    ON client_onboarding.platform_access_type_versions (organization_id, access_type_id)
    WHERE status='DRAFT';
CREATE INDEX ix_platform_access_type_versions_type
    ON client_onboarding.platform_access_type_versions (organization_id, access_type_id, version_number DESC);

CREATE TABLE client_onboarding.platform_access_requests (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    client_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    step_instance_id UUID NOT NULL,
    access_type_id UUID NOT NULL,
    access_type_version_id UUID NOT NULL,
    access_type_code_snapshot VARCHAR(80) NOT NULL,
    access_type_name_snapshot VARCHAR(180) NOT NULL,
    guide_version_number INTEGER NOT NULL CHECK (guide_version_number > 0),
    guide_description_snapshot VARCHAR(2000) NULL,
    instructions_snapshot TEXT NOT NULL,
    help_url_snapshot VARCHAR(2000) NULL,
    resources_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(28) NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (status IN ('NOT_STARTED','REQUESTED','CLIENT_SUBMITTED','UNDER_VERIFICATION','VERIFIED','NEEDS_REVISION','WAIVED')),
    client_account_identifier VARCHAR(320) NULL,
    client_submission_note VARCHAR(4000) NULL,
    submitted_at TIMESTAMPTZ NULL,
    verification_started_at TIMESTAMPTZ NULL,
    verified_at TIMESTAMPTZ NULL,
    revision_requested_at TIMESTAMPTZ NULL,
    waived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL REFERENCES client_onboarding.users(id),
    created_by_type VARCHAR(16) NOT NULL CHECK (created_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_by_type VARCHAR(16) NOT NULL CHECK (updated_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_platform_access_requests_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_platform_access_requests_org_id_project UNIQUE (organization_id, id, project_id),
    CONSTRAINT ux_platform_access_requests_step UNIQUE (organization_id, step_instance_id),
    CONSTRAINT fk_platform_access_requests_project_client FOREIGN KEY (organization_id, project_id, client_id)
        REFERENCES client_onboarding.projects(organization_id, id, client_id),
    CONSTRAINT fk_platform_access_requests_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_platform_access_requests_step FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_platform_access_requests_type FOREIGN KEY (organization_id, access_type_id)
        REFERENCES client_onboarding.platform_access_types(organization_id, id),
    CONSTRAINT fk_platform_access_requests_type_version FOREIGN KEY (organization_id, access_type_version_id, access_type_id)
        REFERENCES client_onboarding.platform_access_type_versions(organization_id, id, access_type_id),
    CONSTRAINT ck_platform_access_requests_resources CHECK (
        jsonb_typeof(resources_snapshot)='array' AND jsonb_array_length(resources_snapshot) <= 12
    ),
    CONSTRAINT ck_platform_access_requests_created_actor CHECK (
        (created_by_type='SYSTEM' AND created_by IS NULL) OR (created_by_type<>'SYSTEM' AND created_by IS NOT NULL)
    ),
    CONSTRAINT ck_platform_access_requests_updated_actor CHECK (
        (updated_by_type='SYSTEM' AND updated_by IS NULL) OR (updated_by_type<>'SYSTEM' AND updated_by IS NOT NULL)
    )
);
CREATE INDEX ix_platform_access_requests_org_status
    ON client_onboarding.platform_access_requests (organization_id, status, updated_at DESC, id DESC);
CREATE INDEX ix_platform_access_requests_project
    ON client_onboarding.platform_access_requests (organization_id, project_id, status, updated_at DESC, id DESC);
CREATE INDEX ix_platform_access_requests_onboarding
    ON client_onboarding.platform_access_requests (organization_id, onboarding_id, step_instance_id);

CREATE TABLE client_onboarding.platform_access_reviews (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    access_request_id UUID NOT NULL,
    project_id UUID NOT NULL,
    action VARCHAR(28) NOT NULL CHECK (action IN ('START_VERIFICATION','VERIFY','REQUEST_REVISION','WAIVE')),
    reason VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ux_platform_access_reviews_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_platform_access_reviews_request FOREIGN KEY (organization_id, access_request_id, project_id)
        REFERENCES client_onboarding.platform_access_requests(organization_id, id, project_id),
    CONSTRAINT fk_platform_access_reviews_actor FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_platform_access_reviews_reason CHECK (
        action NOT IN ('REQUEST_REVISION','WAIVE') OR (reason IS NOT NULL AND length(btrim(reason)) > 0)
    )
);
CREATE INDEX ix_platform_access_reviews_request
    ON client_onboarding.platform_access_reviews (organization_id, access_request_id, created_at, id);

-- Published guide text must remain reproducible for workflows and access requests.
CREATE OR REPLACE FUNCTION client_onboarding.protect_published_platform_access_guide()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.status='PUBLISHED' THEN RAISE EXCEPTION 'published platform access guide versions are immutable'; END IF;
        RETURN OLD;
    END IF;
    IF OLD.status='PUBLISHED' AND (
        NEW.status <> OLD.status OR NEW.access_type_id <> OLD.access_type_id OR NEW.version_number <> OLD.version_number OR
        NEW.instructions_markdown <> OLD.instructions_markdown OR
        NEW.help_url IS DISTINCT FROM OLD.help_url OR NEW.resources_json <> OLD.resources_json OR
        NEW.published_at IS DISTINCT FROM OLD.published_at OR NEW.published_by IS DISTINCT FROM OLD.published_by
    ) THEN
        RAISE EXCEPTION 'published platform access guide versions are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_platform_access_type_versions_immutable
BEFORE UPDATE OR DELETE ON client_onboarding.platform_access_type_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_published_platform_access_guide();

CREATE OR REPLACE FUNCTION client_onboarding.prevent_platform_access_review_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform_access_reviews are append-only';
END;
$$;
CREATE TRIGGER trg_platform_access_reviews_no_mutation
BEFORE UPDATE OR DELETE ON client_onboarding.platform_access_reviews
FOR EACH ROW EXECUTE FUNCTION client_onboarding.prevent_platform_access_review_mutation();

CREATE OR REPLACE FUNCTION client_onboarding.validate_platform_access_request_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.created_by_type IS DISTINCT FROM OLD.created_by_type THEN
        IF NEW.created_by_type='INTERNAL' AND NOT EXISTS (
            SELECT 1 FROM client_onboarding.organization_users ou
            WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE'
        ) THEN RAISE EXCEPTION 'platform access creator is not an active internal organization user'; END IF;
        IF NEW.created_by_type='CLIENT' AND NOT EXISTS (
            SELECT 1
            FROM client_onboarding.client_users cu
            JOIN client_onboarding.client_user_projects cup
              ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id
            WHERE cu.organization_id=NEW.organization_id
              AND cu.user_id=NEW.created_by
              AND cu.client_id=NEW.client_id
              AND cu.status='ACTIVE'
              AND cup.project_id=NEW.project_id
              AND cup.status='ACTIVE'
        ) THEN RAISE EXCEPTION 'platform access creator is not an authorized client user'; END IF;
    END IF;
    IF NEW.updated_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'platform access updater is not an active internal organization user'; END IF;
    IF NEW.updated_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1
        FROM client_onboarding.client_users cu
        JOIN client_onboarding.client_user_projects cup
          ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id
        WHERE cu.organization_id=NEW.organization_id
          AND cu.user_id=NEW.updated_by
          AND cu.client_id=NEW.client_id
          AND cu.status='ACTIVE'
          AND cup.project_id=NEW.project_id
          AND cup.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'platform access updater is not an authorized client user'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_platform_access_requests_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, project_id, client_id, created_by, created_by_type, updated_by, updated_by_type
ON client_onboarding.platform_access_requests
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_platform_access_request_actor();

CREATE OR REPLACE FUNCTION client_onboarding.validate_platform_access_workflow_step()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM client_onboarding.onboarding_step_instances osi
        WHERE osi.organization_id=NEW.organization_id
          AND osi.id=NEW.step_instance_id
          AND osi.onboarding_id=NEW.onboarding_id
          AND osi.step_type='PLATFORM_ACCESS'
    ) THEN RAISE EXCEPTION 'platform access request is linked to a non-PLATFORM_ACCESS workflow step'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_platform_access_requests_validate_step
BEFORE INSERT OR UPDATE OF organization_id, onboarding_id, step_instance_id
ON client_onboarding.platform_access_requests
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_platform_access_workflow_step();
