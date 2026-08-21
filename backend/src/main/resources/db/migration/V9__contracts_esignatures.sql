-- Phase 8: contract templates, immutable legal snapshots, e-signature provider callbacks,
-- signatory evidence and private signed-document retention.

CREATE TABLE client_onboarding.contract_templates (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_contract_templates_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_contract_templates_org_name UNIQUE (organization_id, name),
    CONSTRAINT fk_contract_templates_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_contract_templates_updater FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_contract_templates_org_status ON client_onboarding.contract_templates (organization_id, status, updated_at DESC, id DESC);

CREATE TABLE client_onboarding.contract_template_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    template_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    title VARCHAR(240) NOT NULL,
    legal_content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    published_at TIMESTAMPTZ NULL,
    published_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_contract_template_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_contract_template_versions_org_id_template UNIQUE (organization_id, id, template_id),
    CONSTRAINT ux_contract_template_versions_number UNIQUE (organization_id, template_id, version_number),
    CONSTRAINT fk_contract_template_versions_template FOREIGN KEY (organization_id, template_id)
        REFERENCES client_onboarding.contract_templates(organization_id, id),
    CONSTRAINT fk_contract_template_versions_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_contract_template_versions_updater FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_contract_template_versions_publisher FOREIGN KEY (organization_id, published_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_contract_template_versions_publish CHECK (
        (status='PUBLISHED' AND published_at IS NOT NULL AND published_by IS NOT NULL)
        OR (status<>'PUBLISHED')
    )
);
CREATE UNIQUE INDEX ux_contract_template_versions_one_draft
    ON client_onboarding.contract_template_versions (organization_id, template_id) WHERE status='DRAFT';
CREATE INDEX ix_contract_template_versions_template ON client_onboarding.contract_template_versions (organization_id, template_id, version_number DESC);

CREATE TABLE client_onboarding.contracts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    client_id UUID NOT NULL,
    onboarding_id UUID NULL,
    step_instance_id UUID NULL,
    template_id UUID NOT NULL,
    template_version_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','GENERATED','SENT','VIEWED','SIGNED','DECLINED','EXPIRED','VOID','CANCELLED')),
    subject VARCHAR(240) NOT NULL,
    provider VARCHAR(40) NULL,
    provider_document_id VARCHAR(180) NULL,
    provider_signing_url VARCHAR(2000) NULL,
    send_idempotency_key VARCHAR(128) NULL,
    send_reserved_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NULL,
    generated_at TIMESTAMPTZ NULL,
    sent_at TIMESTAMPTZ NULL,
    viewed_at TIMESTAMPTZ NULL,
    signed_at TIMESTAMPTZ NULL,
    declined_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    creation_idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_by_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' CHECK (updated_by_type IN ('INTERNAL','CLIENT','PROVIDER','SYSTEM')),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_contracts_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_contracts_org_id_project UNIQUE (organization_id, id, project_id),
    CONSTRAINT ux_contracts_org_id_client UNIQUE (organization_id, id, client_id),
    CONSTRAINT ux_contracts_org_id_template_version UNIQUE (organization_id, id, template_version_id),
    CONSTRAINT ux_contracts_creation_request UNIQUE (organization_id, creation_idempotency_key),
    CONSTRAINT fk_contracts_project_client FOREIGN KEY (organization_id, project_id, client_id)
        REFERENCES client_onboarding.projects(organization_id, id, client_id),
    CONSTRAINT fk_contracts_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_contracts_onboarding_step FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_contracts_template FOREIGN KEY (organization_id, template_id)
        REFERENCES client_onboarding.contract_templates(organization_id, id),
    CONSTRAINT fk_contracts_template_version FOREIGN KEY (organization_id, template_version_id, template_id)
        REFERENCES client_onboarding.contract_template_versions(organization_id, id, template_id),
    CONSTRAINT fk_contracts_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_contracts_step_pair CHECK ((onboarding_id IS NULL) = (step_instance_id IS NULL)),
    CONSTRAINT ck_contracts_updater CHECK (
        (updated_by_type IN ('INTERNAL','CLIENT') AND updated_by IS NOT NULL)
        OR (updated_by_type IN ('PROVIDER','SYSTEM') AND updated_by IS NULL)
    ),
    CONSTRAINT ck_contracts_provider_fields CHECK (
        (status='DRAFT' AND provider IS NULL AND provider_document_id IS NULL AND provider_signing_url IS NULL AND send_idempotency_key IS NULL AND send_reserved_at IS NULL AND sent_at IS NULL)
        OR (status='GENERATED' AND provider IS NULL AND provider_document_id IS NULL AND provider_signing_url IS NULL AND sent_at IS NULL AND ((send_idempotency_key IS NULL AND send_reserved_at IS NULL) OR (send_idempotency_key IS NOT NULL AND send_reserved_at IS NOT NULL)))
        OR (status IN ('SENT','VIEWED','SIGNED','DECLINED','EXPIRED') AND provider IS NOT NULL AND provider_document_id IS NOT NULL AND provider_signing_url IS NOT NULL AND send_idempotency_key IS NOT NULL AND send_reserved_at IS NOT NULL AND sent_at IS NOT NULL)
        OR status IN ('VOID','CANCELLED')
    )
);
CREATE UNIQUE INDEX ux_contracts_provider_document ON client_onboarding.contracts (provider, provider_document_id)
    WHERE provider_document_id IS NOT NULL;
CREATE INDEX ix_contracts_org_status ON client_onboarding.contracts (organization_id, status, created_at DESC, id DESC);
CREATE INDEX ix_contracts_project_status ON client_onboarding.contracts (organization_id, project_id, status, created_at DESC, id DESC);
CREATE INDEX ix_contracts_step ON client_onboarding.contracts (organization_id, step_instance_id, created_at DESC, id DESC) WHERE step_instance_id IS NOT NULL;

CREATE TABLE client_onboarding.contract_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    project_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    template_version_id UUID NOT NULL,
    title VARCHAR(240) NOT NULL,
    legal_content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    provider_document_id VARCHAR(180) NULL,
    sent_at TIMESTAMPTZ NULL,
    signed_document_bucket VARCHAR(128) NULL,
    signed_document_key VARCHAR(1000) NULL,
    signed_document_sha256 CHAR(64) NULL CHECK (signed_document_sha256 IS NULL OR signed_document_sha256 ~ '^[0-9a-f]{64}$'),
    signed_document_size BIGINT NULL CHECK (signed_document_size IS NULL OR signed_document_size > 0),
    signed_document_content_type VARCHAR(120) NULL,
    signed_document_stored_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ux_contract_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_contract_versions_contract_number UNIQUE (organization_id, contract_id, version_number),
    CONSTRAINT ux_contract_versions_contract_id UNIQUE (organization_id, contract_id, id),
    CONSTRAINT fk_contract_versions_contract_project FOREIGN KEY (organization_id, contract_id, project_id)
        REFERENCES client_onboarding.contracts(organization_id, id, project_id),
    CONSTRAINT fk_contract_versions_contract_template_version FOREIGN KEY (organization_id, contract_id, template_version_id)
        REFERENCES client_onboarding.contracts(organization_id, id, template_version_id),
    CONSTRAINT fk_contract_versions_template_version FOREIGN KEY (organization_id, template_version_id)
        REFERENCES client_onboarding.contract_template_versions(organization_id, id),
    CONSTRAINT fk_contract_versions_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_contract_versions_signed_document CHECK (
        (signed_document_key IS NULL AND signed_document_bucket IS NULL AND signed_document_sha256 IS NULL AND signed_document_size IS NULL AND signed_document_content_type IS NULL AND signed_document_stored_at IS NULL)
        OR (signed_document_key IS NOT NULL AND signed_document_bucket IS NOT NULL AND signed_document_sha256 IS NOT NULL AND signed_document_size IS NOT NULL AND signed_document_content_type='application/pdf' AND signed_document_stored_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_contract_versions_provider_document ON client_onboarding.contract_versions (provider_document_id)
    WHERE provider_document_id IS NOT NULL;
CREATE INDEX ix_contract_versions_contract ON client_onboarding.contract_versions (organization_id, contract_id, version_number DESC);

CREATE TABLE client_onboarding.contract_recipients (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    client_id UUID NOT NULL,
    contact_id UUID NULL,
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    signing_order INTEGER NOT NULL DEFAULT 0 CHECK (signing_order >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','VIEWED','SIGNED','DECLINED')),
    provider_recipient_id VARCHAR(180) NULL,
    viewed_at TIMESTAMPTZ NULL,
    signed_at TIMESTAMPTZ NULL,
    declined_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ux_contract_recipients_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_contract_recipients_email UNIQUE (organization_id, contract_id, normalized_email),
    CONSTRAINT fk_contract_recipients_contract_client FOREIGN KEY (organization_id, contract_id, client_id)
        REFERENCES client_onboarding.contracts(organization_id, id, client_id),
    CONSTRAINT fk_contract_recipients_contact FOREIGN KEY (organization_id, client_id, contact_id)
        REFERENCES client_onboarding.client_contacts(organization_id, client_id, id),
    CONSTRAINT fk_contract_recipients_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE UNIQUE INDEX ux_contract_recipients_provider ON client_onboarding.contract_recipients (contract_id, provider_recipient_id)
    WHERE provider_recipient_id IS NOT NULL;
CREATE INDEX ix_contract_recipients_contract ON client_onboarding.contract_recipients (organization_id, contract_id, signing_order, id);

CREATE TABLE client_onboarding.contract_signatures (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    contract_version_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(180) NOT NULL,
    provider_signature_id VARCHAR(180) NULL,
    signatory_name VARCHAR(160) NOT NULL,
    signatory_email VARCHAR(320) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_contract_signatures_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_contract_signatures_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT ux_contract_signatures_recipient UNIQUE (organization_id, contract_id, recipient_id),
    CONSTRAINT fk_contract_signatures_contract_version FOREIGN KEY (organization_id, contract_id, contract_version_id)
        REFERENCES client_onboarding.contract_versions(organization_id, contract_id, id),
    CONSTRAINT fk_contract_signatures_recipient FOREIGN KEY (organization_id, recipient_id)
        REFERENCES client_onboarding.contract_recipients(organization_id, id)
);
CREATE INDEX ix_contract_signatures_contract ON client_onboarding.contract_signatures (organization_id, contract_id, signed_at, id);

-- Published legal template text may never be rewritten in place.
CREATE OR REPLACE FUNCTION client_onboarding.protect_published_contract_template_version()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status='PUBLISHED' AND (
        NEW.template_id <> OLD.template_id OR NEW.version_number <> OLD.version_number OR
        NEW.title <> OLD.title OR NEW.legal_content <> OLD.legal_content OR NEW.content_hash <> OLD.content_hash OR
        NEW.published_at IS DISTINCT FROM OLD.published_at OR NEW.published_by IS DISTINCT FROM OLD.published_by
    ) THEN RAISE EXCEPTION 'published contract template versions are immutable'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_contract_template_versions_immutable
BEFORE UPDATE ON client_onboarding.contract_template_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_published_contract_template_version();

-- Generated contract legal text is an immutable evidence snapshot. Only provider/signed-document metadata may advance.
CREATE OR REPLACE FUNCTION client_onboarding.protect_contract_version_legal_content()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organization_id <> OLD.organization_id OR NEW.contract_id <> OLD.contract_id OR NEW.project_id <> OLD.project_id OR
       NEW.version_number <> OLD.version_number OR NEW.template_version_id <> OLD.template_version_id OR
       NEW.title <> OLD.title OR NEW.legal_content <> OLD.legal_content OR NEW.content_hash <> OLD.content_hash OR
       NEW.created_at <> OLD.created_at OR NEW.created_by <> OLD.created_by THEN
       RAISE EXCEPTION 'generated contract legal content is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_contract_versions_legal_content_immutable
BEFORE UPDATE ON client_onboarding.contract_versions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_contract_version_legal_content();

CREATE OR REPLACE FUNCTION client_onboarding.validate_contract_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.updated_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'contract updater is not an active internal organization user'; END IF;
    IF NEW.updated_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        JOIN client_onboarding.client_user_projects cup
          ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id AND cup.project_id=NEW.project_id
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.updated_by AND cu.status='ACTIVE' AND cup.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'contract updater is not an authorized client user'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_contracts_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, project_id, updated_by, updated_by_type ON client_onboarding.contracts
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_contract_actor();

-- Keep the configured workflow type honest: a contract instance linked to a step must target a CONTRACT step.
CREATE OR REPLACE FUNCTION client_onboarding.validate_contract_workflow_step()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.step_instance_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.onboarding_step_instances osi
        WHERE osi.organization_id=NEW.organization_id AND osi.id=NEW.step_instance_id
          AND osi.onboarding_id=NEW.onboarding_id AND osi.step_type='CONTRACT'
    ) THEN RAISE EXCEPTION 'contract is linked to a non-CONTRACT workflow step'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_contracts_validate_workflow_step
BEFORE INSERT OR UPDATE OF organization_id, onboarding_id, step_instance_id ON client_onboarding.contracts
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_contract_workflow_step();
