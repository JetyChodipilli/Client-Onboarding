-- Phase 6: secure asset requirements, direct-to-object-storage upload sessions, immutable file versions,
-- malware/signature validation state, review history, and tenant/project-scoped access.

INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000034', 'ASSET_READ', 'ASSETS', 'Read project asset requirements, metadata, versions, and scan/review state.');

INSERT INTO client_onboarding.role_permissions (organization_id, role_id, permission_id, created_by)
SELECT r.organization_id, r.id, p.id, r.created_by
FROM client_onboarding.roles r
CROSS JOIN client_onboarding.permissions p
WHERE r.system_role = TRUE
  AND r.code = 'ORGANIZATION_ADMIN'
  AND r.status = 'ACTIVE'
  AND p.code = 'ASSET_READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE client_onboarding.asset_requirements (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    step_instance_id UUID NOT NULL,
    form_submission_id UUID NULL,
    form_field_id UUID NULL,
    requirement_key VARCHAR(120) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NULL,
    allowed_mime_types JSONB NOT NULL,
    max_file_size_bytes BIGINT NOT NULL CHECK (max_file_size_bytes > 0 AND max_file_size_bytes <= 1073741824),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    requires_review BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_asset_requirements_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_asset_requirements_scope UNIQUE (organization_id, id, project_id, onboarding_id, step_instance_id),
    CONSTRAINT ux_asset_requirements_step_key UNIQUE (organization_id, step_instance_id, requirement_key),
    CONSTRAINT fk_asset_requirements_onboarding_step FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_asset_requirements_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_asset_requirements_form_submission FOREIGN KEY (organization_id, form_submission_id)
        REFERENCES client_onboarding.form_submissions(organization_id, id),
    CONSTRAINT fk_asset_requirements_form_field FOREIGN KEY (organization_id, form_field_id)
        REFERENCES client_onboarding.form_fields(organization_id, id),
    CONSTRAINT ck_asset_requirements_mimes CHECK (jsonb_typeof(allowed_mime_types)='array' AND jsonb_array_length(allowed_mime_types) BETWEEN 1 AND 40),
    CONSTRAINT ck_asset_requirements_form_pair CHECK ((form_submission_id IS NULL) = (form_field_id IS NULL))
);
CREATE INDEX ix_asset_requirements_project ON client_onboarding.asset_requirements (organization_id, project_id, created_at, id);
CREATE INDEX ix_asset_requirements_onboarding ON client_onboarding.asset_requirements (organization_id, onboarding_id, step_instance_id);

CREATE TABLE client_onboarding.assets (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    project_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    step_instance_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','UPLOADED','SCANNING','SUBMITTED','UNDER_REVIEW','NEEDS_REVISION','REPLACED','APPROVED','QUARANTINED','REJECTED')),
    current_version_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_by_type VARCHAR(16) NOT NULL DEFAULT 'CLIENT' CHECK (updated_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_assets_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_assets_scope UNIQUE (organization_id, id, requirement_id, project_id),
    CONSTRAINT ux_assets_requirement UNIQUE (organization_id, requirement_id),
    CONSTRAINT fk_assets_requirement FOREIGN KEY (organization_id, requirement_id)
        REFERENCES client_onboarding.asset_requirements(organization_id, id),
    CONSTRAINT fk_assets_requirement_scope FOREIGN KEY (organization_id, requirement_id, project_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.asset_requirements(organization_id, id, project_id, onboarding_id, step_instance_id),
    CONSTRAINT fk_assets_onboarding_step FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_assets_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT ck_assets_actor CHECK ((updated_by_type='SYSTEM' AND updated_by IS NULL) OR (updated_by_type<>'SYSTEM' AND updated_by IS NOT NULL))
);
CREATE INDEX ix_assets_project_status ON client_onboarding.assets (organization_id, project_id, status, updated_at DESC, id DESC);
CREATE INDEX ix_assets_step ON client_onboarding.assets (organization_id, step_instance_id, status);

CREATE TABLE client_onboarding.asset_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    project_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    original_filename VARCHAR(255) NOT NULL,
    sanitized_filename VARCHAR(180) NOT NULL,
    storage_bucket VARCHAR(160) NOT NULL,
    object_key VARCHAR(600) NOT NULL,
    expected_size_bytes BIGINT NOT NULL CHECK (expected_size_bytes > 0),
    actual_size_bytes BIGINT NULL CHECK (actual_size_bytes IS NULL OR actual_size_bytes > 0),
    declared_mime_type VARCHAR(160) NOT NULL,
    detected_mime_type VARCHAR(160) NULL,
    expected_sha256 CHAR(64) NULL,
    actual_sha256 CHAR(64) NULL,
    object_etag VARCHAR(180) NULL,
    upload_idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_UPLOAD'
        CHECK (status IN ('PENDING_UPLOAD','UPLOADED','SCAN_PENDING','SCANNING','CLEAN','SCAN_FAILED','QUARANTINED','REJECTED')),
    upload_expires_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ NULL,
    scan_started_at TIMESTAMPTZ NULL,
    scanned_at TIMESTAMPTZ NULL,
    scan_attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (scan_attempt_count >= 0),
    next_scan_at TIMESTAMPTZ NULL,
    malware_signature VARCHAR(500) NULL,
    rejection_reason VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_asset_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_asset_versions_asset_id UNIQUE (organization_id, asset_id, id),
    CONSTRAINT ux_asset_versions_number UNIQUE (organization_id, asset_id, version_number),
    CONSTRAINT ux_asset_versions_object UNIQUE (storage_bucket, object_key),
    CONSTRAINT ux_asset_versions_upload_request UNIQUE (organization_id, requirement_id, upload_idempotency_key),
    CONSTRAINT fk_asset_versions_asset FOREIGN KEY (organization_id, asset_id)
        REFERENCES client_onboarding.assets(organization_id, id),
    CONSTRAINT fk_asset_versions_asset_scope FOREIGN KEY (organization_id, asset_id, requirement_id, project_id)
        REFERENCES client_onboarding.assets(organization_id, id, requirement_id, project_id),
    CONSTRAINT fk_asset_versions_requirement FOREIGN KEY (organization_id, requirement_id)
        REFERENCES client_onboarding.asset_requirements(organization_id, id),
    CONSTRAINT fk_asset_versions_project FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id),
    CONSTRAINT ck_asset_versions_sha CHECK (
        (expected_sha256 IS NULL OR expected_sha256 ~ '^[0-9a-f]{64}$')
        AND (actual_sha256 IS NULL OR actual_sha256 ~ '^[0-9a-f]{64}$')
    )
);
CREATE INDEX ix_asset_versions_asset_history ON client_onboarding.asset_versions (organization_id, asset_id, version_number DESC);
CREATE UNIQUE INDEX ux_asset_versions_one_pending ON client_onboarding.asset_versions (organization_id, asset_id) WHERE status='PENDING_UPLOAD';
CREATE INDEX ix_asset_versions_scan_queue ON client_onboarding.asset_versions (status, next_scan_at, created_at, id)
    WHERE status IN ('SCAN_PENDING','SCAN_FAILED');
CREATE INDEX ix_asset_versions_upload_expiry ON client_onboarding.asset_versions (organization_id, upload_expires_at)
    WHERE status='PENDING_UPLOAD';

ALTER TABLE client_onboarding.assets
    ADD CONSTRAINT fk_assets_current_version
    FOREIGN KEY (organization_id, id, current_version_id)
    REFERENCES client_onboarding.asset_versions(organization_id, asset_id, id);

CREATE TABLE client_onboarding.asset_reviews (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    asset_version_id UUID NOT NULL,
    reviewer_user_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL CHECK (action IN ('REVIEW_STARTED','APPROVED','REVISION_REQUESTED','REJECTED','SCAN_RETRY_REQUESTED')),
    note VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_asset_reviews_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_asset_reviews_asset FOREIGN KEY (organization_id, asset_id)
        REFERENCES client_onboarding.assets(organization_id, id),
    CONSTRAINT fk_asset_reviews_version FOREIGN KEY (organization_id, asset_id, asset_version_id)
        REFERENCES client_onboarding.asset_versions(organization_id, asset_id, id),
    CONSTRAINT fk_asset_reviews_reviewer FOREIGN KEY (organization_id, reviewer_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_asset_reviews_asset ON client_onboarding.asset_reviews (organization_id, asset_id, created_at DESC, id DESC);

-- FORM file requirements must point to the exact draft submission/field/version and project/step captured by that response.
CREATE OR REPLACE FUNCTION client_onboarding.validate_asset_requirement_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_submission_step UUID;
    v_submission_project UUID;
    v_submission_version UUID;
    v_field_version UUID;
BEGIN
    IF NEW.form_submission_id IS NOT NULL THEN
        SELECT step_instance_id, project_id, form_version_id
          INTO v_submission_step, v_submission_project, v_submission_version
          FROM client_onboarding.form_submissions
         WHERE organization_id=NEW.organization_id AND id=NEW.form_submission_id;
        SELECT form_version_id INTO v_field_version
          FROM client_onboarding.form_fields
         WHERE organization_id=NEW.organization_id AND id=NEW.form_field_id;
        IF v_submission_step IS NULL OR v_field_version IS NULL
           OR v_submission_step <> NEW.step_instance_id
           OR v_submission_project <> NEW.project_id
           OR v_submission_version <> v_field_version THEN
            RAISE EXCEPTION 'asset requirement form scope does not match submission';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_asset_requirements_validate_scope
BEFORE INSERT OR UPDATE OF organization_id, project_id, onboarding_id, step_instance_id, form_submission_id, form_field_id
ON client_onboarding.asset_requirements
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_asset_requirement_scope();

-- All user-driven asset mutations must come from an active identity in the same tenant. System scanner updates use NULL actor.
CREATE OR REPLACE FUNCTION client_onboarding.validate_asset_actor()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor UUID;
BEGIN
    IF TG_TABLE_NAME='assets' AND NEW.updated_by_type='SYSTEM' THEN
        RETURN NEW;
    END IF;
    v_actor := COALESCE(NEW.updated_by, NEW.created_by);
    IF v_actor IS NULL OR NOT EXISTS (SELECT 1 FROM client_onboarding.users u WHERE u.id=v_actor AND u.status='ACTIVE') THEN
        RAISE EXCEPTION 'asset actor is not an active identity';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM client_onboarding.organization_users ou WHERE ou.organization_id=NEW.organization_id AND ou.user_id=v_actor AND ou.status='ACTIVE')
       AND NOT EXISTS (SELECT 1 FROM client_onboarding.client_users cu WHERE cu.organization_id=NEW.organization_id AND cu.user_id=v_actor AND cu.status='ACTIVE') THEN
        RAISE EXCEPTION 'asset actor is not active in organization';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_asset_requirements_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, created_by, updated_by ON client_onboarding.asset_requirements
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_asset_actor();
CREATE TRIGGER trg_assets_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, created_by, updated_by, updated_by_type ON client_onboarding.assets
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_asset_actor();


CREATE OR REPLACE FUNCTION client_onboarding.validate_asset_version_creator()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM client_onboarding.users u WHERE u.id=NEW.created_by AND u.status='ACTIVE') THEN
        RAISE EXCEPTION 'asset version creator is not an active identity';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM client_onboarding.organization_users ou WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE')
       AND NOT EXISTS (SELECT 1 FROM client_onboarding.client_users cu WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.created_by AND cu.status='ACTIVE') THEN
        RAISE EXCEPTION 'asset version creator is not active in organization';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_asset_versions_validate_creator
BEFORE INSERT OR UPDATE OF organization_id, created_by ON client_onboarding.asset_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_asset_version_creator();

-- A version is append-only after upload; object identity/file metadata can never be rewritten to point at another object.
CREATE OR REPLACE FUNCTION client_onboarding.protect_asset_version_object_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'PENDING_UPLOAD' AND (
        NEW.storage_bucket <> OLD.storage_bucket OR NEW.object_key <> OLD.object_key OR
        NEW.original_filename <> OLD.original_filename OR NEW.sanitized_filename <> OLD.sanitized_filename OR
        NEW.expected_size_bytes <> OLD.expected_size_bytes OR NEW.declared_mime_type <> OLD.declared_mime_type OR
        NEW.expected_sha256 IS DISTINCT FROM OLD.expected_sha256
    ) THEN
        RAISE EXCEPTION 'uploaded asset version object identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_asset_versions_protect_object_identity
BEFORE UPDATE ON client_onboarding.asset_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_asset_version_object_identity();
