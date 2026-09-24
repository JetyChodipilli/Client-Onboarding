INSERT INTO permissions(id,code,description,created_at) VALUES
 ('00000000-0000-0000-0000-000000000034','ASSET_READ','Read project assets and requirements',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000035','ASSET_MANAGE','Manage asset requirements',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000036','ASSET_REVIEW','Review, revise and approve assets',CURRENT_TIMESTAMP);

CREATE TABLE asset_requirements (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
 name varchar(180) NOT NULL, instructions varchar(2000), allowed_mimes text NOT NULL,
 max_bytes bigint NOT NULL CHECK(max_bytes BETWEEN 1 AND 52428800), archived_at timestamptz,
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,name)
);
CREATE TABLE assets (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, step_id uuid NOT NULL, requirement_id uuid NOT NULL,
 current_version_id uuid, created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,step_id),
 FOREIGN KEY(organization_id,step_id) REFERENCES onboarding_step_instances(organization_id,id),
 FOREIGN KEY(organization_id,requirement_id) REFERENCES asset_requirements(organization_id,id)
);
CREATE TABLE asset_versions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, asset_id uuid NOT NULL,
 version_number integer NOT NULL CHECK(version_number BETWEEN 1 AND 1000),
 filename varchar(180) NOT NULL, declared_mime varchar(100) NOT NULL, detected_mime varchar(100),
 byte_size bigint NOT NULL CHECK(byte_size BETWEEN 1 AND 52428800), sha256 varchar(64) NOT NULL,
 object_key varchar(250) NOT NULL UNIQUE, object_version_id varchar(1024),
 status varchar(24) NOT NULL CHECK(status IN ('REQUESTED','UPLOADED','SCANNING','SUBMITTED','UNDER_REVIEW','APPROVED','NEEDS_REVISION','REPLACED','QUARANTINED','REJECTED')),
 scan_status varchar(16) NOT NULL CHECK(scan_status IN ('PENDING','SCANNING','CLEAN','INFECTED','ERROR')),
 scan_message varchar(1000), review_note varchar(2000), upload_expires_at timestamptz NOT NULL,
 scan_lease_id uuid, scan_lease_until timestamptz, scanned_at timestamptz,
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,asset_id,id), UNIQUE(organization_id,asset_id,version_number),
 FOREIGN KEY(organization_id,asset_id) REFERENCES assets(organization_id,id),
 CHECK(scan_status <> 'CLEAN' OR (object_version_id IS NOT NULL AND detected_mime IS NOT NULL AND scanned_at IS NOT NULL))
);
ALTER TABLE assets ADD CONSTRAINT fk_asset_current_version FOREIGN KEY(organization_id,id,current_version_id)
 REFERENCES asset_versions(organization_id,asset_id,id);
CREATE TABLE asset_reviews (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, asset_version_id uuid NOT NULL,
 decision varchar(24) NOT NULL CHECK(decision IN ('UNDER_REVIEW','APPROVED','NEEDS_REVISION','REOPENED','SKIPPED')),
 note varchar(2000), created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 FOREIGN KEY(organization_id,asset_version_id) REFERENCES asset_versions(organization_id,id)
);
CREATE TABLE asset_outbox_events (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, asset_id uuid NOT NULL, asset_version_id uuid,
 event_type varchar(40) NOT NULL, occurred_at timestamptz NOT NULL, correlation_id varchar(36) NOT NULL,
 payload_version integer NOT NULL DEFAULT 1, processed_at timestamptz,
 FOREIGN KEY(organization_id,asset_id) REFERENCES assets(organization_id,id),
 FOREIGN KEY(organization_id,asset_version_id) REFERENCES asset_versions(organization_id,id)
);
CREATE INDEX ix_asset_requirements_list ON asset_requirements(organization_id,created_at DESC,id);
CREATE INDEX ix_asset_reviews_version ON asset_reviews(organization_id,asset_version_id,created_at,id);
CREATE INDEX ix_asset_outbox_pending ON asset_outbox_events(occurred_at,id) WHERE processed_at IS NULL;

CREATE FUNCTION guard_asset_requirement() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR ROW(NEW.name,NEW.instructions,NEW.allowed_mimes,NEW.max_bytes,NEW.organization_id)
    IS DISTINCT FROM ROW(OLD.name,OLD.instructions,OLD.allowed_mimes,OLD.max_bytes,OLD.organization_id)
 THEN RAISE EXCEPTION 'Asset requirement content is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_asset_requirement BEFORE UPDATE OR DELETE ON asset_requirements
 FOR EACH ROW EXECUTE FUNCTION guard_asset_requirement();
CREATE FUNCTION guard_asset_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Asset versions are retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.organization_id,NEW.asset_id,NEW.version_number,NEW.filename,NEW.declared_mime,NEW.byte_size,NEW.sha256,NEW.object_key)
 IS DISTINCT FROM ROW(OLD.organization_id,OLD.asset_id,OLD.version_number,OLD.filename,OLD.declared_mime,OLD.byte_size,OLD.sha256,OLD.object_key)
 OR (OLD.object_version_id IS NOT NULL AND NEW.object_version_id IS DISTINCT FROM OLD.object_version_id)
 THEN RAISE EXCEPTION 'Asset file identity is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_asset_file BEFORE UPDATE OR DELETE ON asset_versions
 FOR EACH ROW EXECUTE FUNCTION guard_asset_version();
CREATE FUNCTION guard_asset_review() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Asset reviews are append-only' USING ERRCODE='23514';
END $$;
CREATE TRIGGER immutable_asset_review BEFORE UPDATE OR DELETE ON asset_reviews
 FOR EACH ROW EXECUTE FUNCTION guard_asset_review();
