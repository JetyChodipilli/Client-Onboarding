-- Phase 11: final readiness review, immutable review decisions, and project activation hardening.
-- Readiness itself is derived from the immutable onboarding step snapshot: all applicable blocking
-- steps must be COMPLETED. This migration persists human review decisions and adds DB safety rails
-- around READY/ACTIVE project transitions.

CREATE TABLE client_onboarding.onboarding_reviews (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    project_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL
        CHECK (action IN ('REVIEW_STARTED','APPROVED','REVISION_REQUESTED')),
    reviewer_user_id UUID NOT NULL,
    reason VARCHAR(2000) NULL,
    revision_step_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    onboarding_version BIGINT NOT NULL CHECK (onboarding_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_onboarding_reviews_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_onboarding_reviews_onboarding_project
        FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_onboarding_reviews_reviewer
        FOREIGN KEY (organization_id, reviewer_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_onboarding_reviews_revision_steps CHECK (
        jsonb_typeof(revision_step_ids) = 'array'
        AND jsonb_array_length(revision_step_ids) <= 25
    ),
    CONSTRAINT ck_onboarding_reviews_reason CHECK (
        (action = 'REVISION_REQUESTED' AND reason IS NOT NULL AND length(btrim(reason)) > 0
            AND jsonb_array_length(revision_step_ids) > 0)
        OR (action <> 'REVISION_REQUESTED' AND jsonb_array_length(revision_step_ids) = 0)
    )
);
CREATE INDEX ix_onboarding_reviews_onboarding_time
    ON client_onboarding.onboarding_reviews (organization_id, onboarding_id, created_at DESC, id DESC);
CREATE INDEX ix_onboarding_reviews_project_time
    ON client_onboarding.onboarding_reviews (organization_id, project_id, created_at DESC, id DESC);

-- Review decisions are privileged evidence. Validate the reviewer and the lifecycle state at insert time so
-- direct SQL cannot fabricate an approval or revision decision that the application state machine would reject.
CREATE OR REPLACE FUNCTION client_onboarding.validate_onboarding_review_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_onboarding_status VARCHAR(40);
    v_onboarding_version BIGINT;
    v_project_status VARCHAR(24);
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id = NEW.organization_id
          AND ou.user_id = NEW.reviewer_user_id
          AND ou.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'onboarding reviewer is not an active internal organization user';
    END IF;

    SELECT oi.status, oi.version, p.status
      INTO v_onboarding_status, v_onboarding_version, v_project_status
      FROM client_onboarding.onboarding_instances oi
      JOIN client_onboarding.projects p
        ON p.organization_id = oi.organization_id AND p.id = oi.project_id
     WHERE oi.organization_id = NEW.organization_id
       AND oi.id = NEW.onboarding_id
       AND oi.project_id = NEW.project_id;

    IF v_onboarding_status IS NULL THEN
        RAISE EXCEPTION 'onboarding review resource relationship is invalid';
    END IF;
    IF NEW.onboarding_version <> v_onboarding_version THEN
        RAISE EXCEPTION 'onboarding review version does not match current onboarding version';
    END IF;
    IF NEW.action = 'REVIEW_STARTED' AND NOT (v_onboarding_status = 'AWAITING_INTERNAL_REVIEW' AND v_project_status = 'ONBOARDING') THEN
        RAISE EXCEPTION 'final review can start only for reviewable onboarding';
    ELSIF NEW.action = 'REVISION_REQUESTED' AND v_onboarding_status <> 'NEEDS_REVISION' THEN
        RAISE EXCEPTION 'revision review evidence requires NEEDS_REVISION onboarding';
    ELSIF NEW.action = 'APPROVED' AND NOT (v_onboarding_status = 'COMPLETED' AND v_project_status = 'READY') THEN
        RAISE EXCEPTION 'approval evidence requires completed onboarding and READY project';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_onboarding_reviews_validate_insert
BEFORE INSERT ON client_onboarding.onboarding_reviews
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_onboarding_review_insert();

-- Review history is evidence. Corrections are represented by another review row rather than rewriting history.
CREATE OR REPLACE FUNCTION client_onboarding.prevent_onboarding_review_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'onboarding review history is append-only';
END;
$$;
CREATE TRIGGER trg_onboarding_reviews_append_only
BEFORE UPDATE OR DELETE ON client_onboarding.onboarding_reviews
FOR EACH ROW EXECUTE FUNCTION client_onboarding.prevent_onboarding_review_mutation();

-- Project READY must be backed by a completed onboarding instance. ACTIVE must be entered from READY.
-- Resume from ON_HOLD is allowed only when the held state was already ACTIVE.
CREATE OR REPLACE FUNCTION client_onboarding.validate_project_readiness_activation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'READY' AND OLD.status IS DISTINCT FROM 'READY' THEN
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
CREATE TRIGGER trg_projects_readiness_activation_guard
BEFORE UPDATE OF status ON client_onboarding.projects
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_project_readiness_activation();

-- Phase 10 hardening discovered during the Phase 11 audit. Historical creators may later become inactive;
-- that must not brick otherwise valid task/template updates. Validate immutable creator identity only when
-- it is established, while continuing to validate each current updater and assignee on every relevant write.
CREATE OR REPLACE FUNCTION client_onboarding.validate_task_actor_and_assignee()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    validate_creator BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'INSERT' THEN
        validate_creator := TRUE;
    ELSE
        validate_creator := NEW.organization_id IS DISTINCT FROM OLD.organization_id
            OR NEW.created_by IS DISTINCT FROM OLD.created_by
            OR NEW.created_by_type IS DISTINCT FROM OLD.created_by_type;
    END IF;

    IF validate_creator THEN
        IF NEW.created_by_type='INTERNAL' AND NOT EXISTS (
            SELECT 1 FROM client_onboarding.organization_users ou
            WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE'
        ) THEN RAISE EXCEPTION 'task creator is not an active internal organization user'; END IF;
        IF NEW.created_by_type='CLIENT' AND NOT EXISTS (
            SELECT 1 FROM client_onboarding.client_users cu
            WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.created_by AND cu.status='ACTIVE'
        ) THEN RAISE EXCEPTION 'task creator is not an active client user'; END IF;
    END IF;

    IF NEW.updated_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task updater is not an active internal organization user'; END IF;
    IF NEW.updated_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.updated_by AND cu.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task updater is not an active client user'; END IF;
    IF NEW.assigned_user_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.assigned_user_id AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task assignee is not an active internal organization user'; END IF;
    IF NEW.assigned_user_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        LEFT JOIN client_onboarding.client_user_projects cup
          ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.assigned_user_id AND cu.status='ACTIVE'
          AND (NEW.project_id IS NULL OR (cup.project_id=NEW.project_id AND cup.status='ACTIVE'))
    ) THEN RAISE EXCEPTION 'task assignee is not an active client user for this scope'; END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION client_onboarding.validate_notification_template_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    validate_creator BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'INSERT' THEN
        validate_creator := TRUE;
    ELSE
        validate_creator := NEW.organization_id IS DISTINCT FROM OLD.organization_id
            OR NEW.created_by IS DISTINCT FROM OLD.created_by;
    END IF;

    IF validate_creator AND NEW.created_by IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification template creator is not an active internal organization user'; END IF;
    IF NEW.updated_by IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification template updater is not an active internal organization user'; END IF;
    RETURN NEW;
END;
$$;
