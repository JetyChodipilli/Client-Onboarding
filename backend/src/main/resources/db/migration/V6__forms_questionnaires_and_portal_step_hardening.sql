-- Phase 5: reusable/versioned forms, conditional fields, draft/submission/review/revision,
-- immutable historical submission versions, and FORM workflow-step integration.
-- Also hardens the Phase 3 step updater relationship so authenticated client users can truthfully
-- perform client-side step transitions without being misclassified as internal organization users.

INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000031', 'FORM_READ', 'FORM', 'Read form templates, versions, and project form submissions.'),
('00000000-0000-0000-0000-000000000032', 'FORM_MANAGE', 'FORM', 'Create, edit, version, publish, and archive reusable forms.'),
('00000000-0000-0000-0000-000000000033', 'FORM_REVIEW', 'FORM', 'Review, approve, and request revision of submitted client forms.');

INSERT INTO client_onboarding.role_permissions (organization_id, role_id, permission_id, created_by)
SELECT r.organization_id, r.id, p.id, r.created_by
FROM client_onboarding.roles r
CROSS JOIN client_onboarding.permissions p
WHERE r.system_role = TRUE
  AND r.code = 'ORGANIZATION_ADMIN'
  AND r.status = 'ACTIVE'
  AND p.code IN ('FORM_READ','FORM_MANAGE','FORM_REVIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- A FORM step is changed by an authenticated client on draft/submit and by an internal reviewer later.
ALTER TABLE client_onboarding.onboarding_step_instances
    DROP CONSTRAINT fk_onboarding_step_instances_updater_tenant,
    ADD CONSTRAINT fk_onboarding_step_instances_updated_by_user
        FOREIGN KEY (updated_by) REFERENCES client_onboarding.users(id);

CREATE OR REPLACE FUNCTION client_onboarding.validate_onboarding_step_updater()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id = NEW.organization_id
          AND ou.user_id = NEW.updated_by
          AND ou.status = 'ACTIVE'
    ) AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id = NEW.organization_id
          AND cu.user_id = NEW.updated_by
          AND cu.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'onboarding step updater is not active in organization';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_onboarding_step_instances_validate_updater
BEFORE INSERT OR UPDATE OF organization_id, updated_by ON client_onboarding.onboarding_step_instances
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_onboarding_step_updater();

CREATE TABLE client_onboarding.forms (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_forms_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_forms_creator_tenant FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_forms_updater_tenant FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE UNIQUE INDEX ux_forms_active_name_lower
    ON client_onboarding.forms (organization_id, LOWER(name)) WHERE status='ACTIVE';
CREATE INDEX ix_forms_org_status_updated ON client_onboarding.forms (organization_id, status, updated_at DESC, id DESC);

CREATE TABLE client_onboarding.form_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    form_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    change_note VARCHAR(500) NULL,
    published_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_form_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_form_versions_org_id_form UNIQUE (organization_id, id, form_id),
    CONSTRAINT ux_form_versions_number UNIQUE (organization_id, form_id, version_number),
    CONSTRAINT fk_form_versions_form_tenant FOREIGN KEY (organization_id, form_id)
        REFERENCES client_onboarding.forms(organization_id, id),
    CONSTRAINT fk_form_versions_creator_tenant FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_form_versions_updater_tenant FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_form_versions_published CHECK ((status='PUBLISHED' AND published_at IS NOT NULL) OR status='DRAFT')
);
CREATE UNIQUE INDEX ux_form_versions_one_draft ON client_onboarding.form_versions (organization_id, form_id) WHERE status='DRAFT';
CREATE INDEX ix_form_versions_form_status ON client_onboarding.form_versions (organization_id, form_id, status, version_number DESC);

CREATE TABLE client_onboarding.form_fields (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    form_version_id UUID NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    label VARCHAR(220) NOT NULL,
    help_text VARCHAR(1000) NULL,
    field_type VARCHAR(24) NOT NULL CHECK (field_type IN ('TEXT','TEXTAREA','NUMBER','EMAIL','URL','DATE','DROPDOWN','RADIO','CHECKBOX','MULTI_SELECT','FILE','BOOLEAN')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    condition_expression JSONB NOT NULL DEFAULT '{"op":"ALWAYS"}'::jsonb,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_form_fields_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_form_fields_version_id UNIQUE (organization_id, form_version_id, id),
    CONSTRAINT ux_form_fields_key UNIQUE (organization_id, form_version_id, field_key),
    CONSTRAINT ux_form_fields_order UNIQUE (organization_id, form_version_id, display_order),
    CONSTRAINT fk_form_fields_version_tenant FOREIGN KEY (organization_id, form_version_id)
        REFERENCES client_onboarding.form_versions(organization_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_form_fields_creator_tenant FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_form_fields_updater_tenant FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_form_fields_json CHECK (jsonb_typeof(condition_expression)='object' AND jsonb_typeof(configuration_json)='object')
);
CREATE INDEX ix_form_fields_version_order ON client_onboarding.form_fields (organization_id, form_version_id, display_order, id);

-- A logical form response has one row per immutable submitted attempt. The newest DRAFT is mutable;
-- when a reviewer requests revision a new DRAFT is created and linked to the prior attempt.
CREATE TABLE client_onboarding.form_submissions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    step_instance_id UUID NOT NULL,
    project_id UUID NOT NULL,
    client_user_id UUID NOT NULL,
    form_version_id UUID NOT NULL,
    submission_number INTEGER NOT NULL CHECK (submission_number > 0),
    previous_submission_id UUID NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','NEEDS_REVISION','APPROVED')),
    submitted_at TIMESTAMPTZ NULL,
    review_started_at TIMESTAMPTZ NULL,
    reviewed_at TIMESTAMPTZ NULL,
    reviewer_user_id UUID NULL,
    revision_note VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_form_submissions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_form_submissions_step_id UNIQUE (organization_id, step_instance_id, id),
    CONSTRAINT ux_form_submissions_step_number UNIQUE (organization_id, step_instance_id, submission_number),
    CONSTRAINT fk_form_submissions_onboarding_step_tenant FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_form_submissions_onboarding_project_tenant FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_form_submissions_client_project_access FOREIGN KEY (organization_id, client_user_id, project_id)
        REFERENCES client_onboarding.client_user_projects(organization_id, client_user_id, project_id),
    CONSTRAINT fk_form_submissions_form_version_tenant FOREIGN KEY (organization_id, form_version_id)
        REFERENCES client_onboarding.form_versions(organization_id, id),
    CONSTRAINT fk_form_submissions_previous_tenant FOREIGN KEY (organization_id, step_instance_id, previous_submission_id)
        REFERENCES client_onboarding.form_submissions(organization_id, step_instance_id, id),
    CONSTRAINT fk_form_submissions_reviewer_tenant FOREIGN KEY (organization_id, reviewer_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_form_submissions_state_fields CHECK (
        (status='DRAFT' AND submitted_at IS NULL AND reviewed_at IS NULL)
        OR (status='SUBMITTED' AND submitted_at IS NOT NULL AND reviewed_at IS NULL)
        OR (status='UNDER_REVIEW' AND submitted_at IS NOT NULL AND review_started_at IS NOT NULL AND reviewed_at IS NULL)
        OR (status='NEEDS_REVISION' AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL AND reviewer_user_id IS NOT NULL AND revision_note IS NOT NULL)
        OR (status='APPROVED' AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL AND (reviewer_user_id IS NOT NULL OR review_started_at IS NULL))
    )
);
CREATE UNIQUE INDEX ux_form_submissions_one_draft ON client_onboarding.form_submissions (organization_id, step_instance_id) WHERE status='DRAFT';
CREATE INDEX ix_form_submissions_project_status ON client_onboarding.form_submissions (organization_id, project_id, status, updated_at DESC, id DESC);
CREATE INDEX ix_form_submissions_step_history ON client_onboarding.form_submissions (organization_id, step_instance_id, submission_number DESC);

CREATE TABLE client_onboarding.form_answers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    submission_id UUID NOT NULL,
    field_id UUID NOT NULL,
    value_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_form_answers_submission_field UNIQUE (organization_id, submission_id, field_id),
    CONSTRAINT fk_form_answers_submission_tenant FOREIGN KEY (organization_id, submission_id)
        REFERENCES client_onboarding.form_submissions(organization_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_form_answers_field_tenant FOREIGN KEY (organization_id, field_id)
        REFERENCES client_onboarding.form_fields(organization_id, id),
    CONSTRAINT ck_form_answers_json CHECK (jsonb_typeof(value_json) <> 'null')
);
CREATE INDEX ix_form_answers_submission ON client_onboarding.form_answers (organization_id, submission_id);

-- Published form definitions are immutable. Historical submissions also become immutable after leaving DRAFT;
-- review state changes remain allowed on the submission row, but answers can no longer be mutated.
CREATE OR REPLACE FUNCTION client_onboarding.reject_published_form_definition_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(24);
    v_form_version_id UUID;
BEGIN
    v_form_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.form_version_id ELSE NEW.form_version_id END;
    SELECT status INTO v_status FROM client_onboarding.form_versions WHERE id = v_form_version_id;
    IF v_status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'published form versions are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_form_fields_published_immutable
BEFORE INSERT OR UPDATE OR DELETE ON client_onboarding.form_fields
FOR EACH ROW EXECUTE FUNCTION client_onboarding.reject_published_form_definition_mutation();

CREATE OR REPLACE FUNCTION client_onboarding.reject_published_form_version_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status='PUBLISHED' THEN RAISE EXCEPTION 'published form versions are immutable'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_form_versions_published_immutable
BEFORE UPDATE ON client_onboarding.form_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.reject_published_form_version_mutation();

CREATE OR REPLACE FUNCTION client_onboarding.reject_non_draft_answer_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(32);
    v_submission_id UUID;
BEGIN
    v_submission_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.submission_id ELSE NEW.submission_id END;
    SELECT status INTO v_status FROM client_onboarding.form_submissions WHERE id = v_submission_id;
    IF v_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'submitted form answers are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_form_answers_draft_only
BEFORE INSERT OR UPDATE OR DELETE ON client_onboarding.form_answers
FOR EACH ROW EXECUTE FUNCTION client_onboarding.reject_non_draft_answer_mutation();

-- Every submission actor must belong to the same tenant as either an active client user or internal member.
CREATE OR REPLACE FUNCTION client_onboarding.validate_form_submission_actor()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor UUID;
BEGIN
    FOREACH v_actor IN ARRAY ARRAY[NEW.created_by, NEW.updated_by]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM client_onboarding.users u WHERE u.id=v_actor AND u.status='ACTIVE') THEN
            RAISE EXCEPTION 'form submission actor is not an active identity';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM client_onboarding.organization_users ou WHERE ou.organization_id=NEW.organization_id AND ou.user_id=v_actor AND ou.status='ACTIVE')
           AND NOT EXISTS (SELECT 1 FROM client_onboarding.client_users cu WHERE cu.organization_id=NEW.organization_id AND cu.user_id=v_actor AND cu.status='ACTIVE') THEN
            RAISE EXCEPTION 'form submission actor is not active in organization';
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_form_submissions_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, created_by, updated_by ON client_onboarding.form_submissions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_form_submission_actor();

-- Defense in depth: an answer may reference only a field from the exact immutable form version
-- captured by its owning submission. This prevents cross-form/cross-version answer attachment even if
-- application validation is accidentally bypassed.
CREATE OR REPLACE FUNCTION client_onboarding.validate_form_answer_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_submission_version UUID;
    v_field_version UUID;
BEGIN
    SELECT form_version_id INTO v_submission_version
      FROM client_onboarding.form_submissions
     WHERE organization_id=NEW.organization_id AND id=NEW.submission_id;
    SELECT form_version_id INTO v_field_version
      FROM client_onboarding.form_fields
     WHERE organization_id=NEW.organization_id AND id=NEW.field_id;
    IF v_submission_version IS NULL OR v_field_version IS NULL OR v_submission_version <> v_field_version THEN
        RAISE EXCEPTION 'form answer field does not belong to submission form version';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_form_answers_validate_scope
BEFORE INSERT OR UPDATE OF organization_id, submission_id, field_id ON client_onboarding.form_answers
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_form_answer_scope();
