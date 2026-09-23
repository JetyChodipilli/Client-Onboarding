INSERT INTO permissions (id, code, description, created_at) VALUES
 ('00000000-0000-0000-0000-000000000031','FORM_READ','Read form templates and project responses',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000032','FORM_MANAGE','Create and publish form templates',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000033','FORM_REVIEW','Review form submissions and request revisions',CURRENT_TIMESTAMP);

CREATE TABLE forms (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
 name varchar(180) NOT NULL, description varchar(1000), archived_at timestamptz,
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE (organization_id,id), UNIQUE (organization_id,name)
);
CREATE TABLE form_versions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, form_id uuid NOT NULL,
 version_number integer NOT NULL CHECK(version_number > 0),
 status varchar(16) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED')),
 fields_json text NOT NULL CHECK(length(fields_json) <= 100000),
 published_at timestamptz,
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE (organization_id,id), UNIQUE (organization_id,form_id,version_number),
 FOREIGN KEY (organization_id,form_id) REFERENCES forms(organization_id,id)
);
CREATE UNIQUE INDEX uq_form_draft ON form_versions(organization_id,form_id) WHERE status='DRAFT';
CREATE TABLE form_responses (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, step_id uuid NOT NULL, form_version_id uuid NOT NULL,
 status varchar(24) NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','NEEDS_REVISION','APPROVED')),
 answers_json text NOT NULL CHECK(length(answers_json) <= 100000),
 submission_number integer NOT NULL DEFAULT 0 CHECK(submission_number >= 0),
 review_note varchar(2000),
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL REFERENCES users(id), version bigint NOT NULL CHECK(version > 0),
 UNIQUE (organization_id,id), UNIQUE (organization_id,step_id),
 FOREIGN KEY (organization_id,step_id) REFERENCES onboarding_step_instances(organization_id,id),
 FOREIGN KEY (organization_id,form_version_id) REFERENCES form_versions(organization_id,id)
);
CREATE TABLE form_submissions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, response_id uuid NOT NULL,
 submission_number integer NOT NULL CHECK(submission_number > 0),
 answers_json text NOT NULL CHECK(length(answers_json) <= 100000),
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 UNIQUE (organization_id,id), UNIQUE (organization_id,response_id,submission_number),
 FOREIGN KEY (organization_id,response_id) REFERENCES form_responses(organization_id,id)
);
CREATE TABLE form_reviews (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, submission_id uuid NOT NULL,
 decision varchar(24) NOT NULL CHECK(decision IN ('UNDER_REVIEW','NEEDS_REVISION','APPROVED')),
 note varchar(2000), created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 FOREIGN KEY (organization_id,submission_id) REFERENCES form_submissions(organization_id,id)
);
CREATE INDEX ix_forms_list ON forms(organization_id,created_at DESC,id);
CREATE INDEX ix_form_reviews_submission ON form_reviews(organization_id,submission_id,created_at);

-- Producer-owned durable events; Phase 10 adds notification delivery workers.
CREATE TABLE form_outbox_events (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
 response_id uuid NOT NULL, event_type varchar(40) NOT NULL,
 submission_number integer NOT NULL, occurred_at timestamptz NOT NULL,
 correlation_id varchar(36) NOT NULL, payload_version integer NOT NULL DEFAULT 1, processed_at timestamptz,
 FOREIGN KEY (organization_id,response_id) REFERENCES form_responses(organization_id,id)
);
CREATE INDEX ix_form_outbox_pending ON form_outbox_events(occurred_at,id) WHERE processed_at IS NULL;

-- Published definitions and submitted answers are immutable, including direct SQL writes.
CREATE FUNCTION guard_form_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status='PUBLISHED' THEN RAISE EXCEPTION 'Published form versions are immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_form_version BEFORE UPDATE OR DELETE ON form_versions
 FOR EACH ROW EXECUTE FUNCTION guard_form_version();
CREATE FUNCTION guard_form_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Form history is append only' USING ERRCODE='23514';
END $$;
CREATE TRIGGER immutable_form_submission BEFORE UPDATE OR DELETE ON form_submissions
 FOR EACH ROW EXECUTE FUNCTION guard_form_history();
CREATE TRIGGER immutable_form_review BEFORE UPDATE OR DELETE ON form_reviews
 FOR EACH ROW EXECUTE FUNCTION guard_form_history();
