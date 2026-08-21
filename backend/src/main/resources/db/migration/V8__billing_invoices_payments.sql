-- Phase 7: billing, invoices, provider-backed payment sessions, immutable payment ledger,
-- partial payments, refunds, reconciliation, verified/idempotent webhooks and workflow integration.

-- The tenant/client composite project key used below was introduced in V5. Do not recreate it here.

-- Financial provider events are auditable system-originated actions with no local user identity.
ALTER TABLE client_onboarding.audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_actor_type_check,
    DROP CONSTRAINT IF EXISTS ck_audit_logs_actor_presence;
ALTER TABLE client_onboarding.audit_logs
    ADD CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('INTERNAL','CLIENT','SYSTEM','PROVIDER')),
    ADD CONSTRAINT ck_audit_logs_actor_presence CHECK (
        (actor_type IN ('SYSTEM','PROVIDER') AND actor_user_id IS NULL)
        OR (actor_type IN ('INTERNAL','CLIENT') AND actor_user_id IS NOT NULL)
    );


-- Automated financial events may legitimately complete/reopen a workflow requirement. Preserve the
-- actor type explicitly rather than attributing provider/system work to an unrelated human user.
ALTER TABLE client_onboarding.onboarding_instances
    DROP CONSTRAINT IF EXISTS fk_onboarding_instances_updated_by_user,
    ALTER COLUMN updated_by DROP NOT NULL,
    ADD COLUMN updated_by_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'
        CHECK (updated_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    ADD CONSTRAINT fk_onboarding_instances_updated_by_user FOREIGN KEY (updated_by) REFERENCES client_onboarding.users(id),
    ADD CONSTRAINT ck_onboarding_instances_updater_presence CHECK (
        (updated_by_type='SYSTEM' AND updated_by IS NULL)
        OR (updated_by_type IN ('INTERNAL','CLIENT') AND updated_by IS NOT NULL)
    );

ALTER TABLE client_onboarding.onboarding_step_instances
    DROP CONSTRAINT IF EXISTS fk_onboarding_step_instances_updated_by_user,
    ALTER COLUMN updated_by DROP NOT NULL,
    ADD COLUMN updated_by_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'
        CHECK (updated_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    ADD CONSTRAINT fk_onboarding_step_instances_updated_by_user FOREIGN KEY (updated_by) REFERENCES client_onboarding.users(id),
    ADD CONSTRAINT ck_onboarding_step_instances_updater_presence CHECK (
        (updated_by_type='SYSTEM' AND updated_by IS NULL)
        OR (updated_by_type IN ('INTERNAL','CLIENT') AND updated_by IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION client_onboarding.validate_onboarding_instance_updater()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.updated_by_type='SYSTEM' THEN RETURN NEW; END IF;
    IF NEW.updated_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'onboarding updater is not active internal organization user'; END IF;
    IF NEW.updated_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.updated_by AND cu.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'onboarding updater is not active client user'; END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION client_onboarding.validate_onboarding_step_updater()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.updated_by_type='SYSTEM' THEN RETURN NEW; END IF;
    IF NEW.updated_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'onboarding step updater is not active internal organization user'; END IF;
    IF NEW.updated_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.updated_by AND cu.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'onboarding step updater is not active client user'; END IF;
    RETURN NEW;
END;
$$;

CREATE TABLE client_onboarding.invoices (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    client_id UUID NOT NULL,
    onboarding_id UUID NULL,
    step_instance_id UUID NULL,
    invoice_number VARCHAR(64) NOT NULL,
    payment_policy VARCHAR(32) NOT NULL CHECK (payment_policy IN ('FULL','DEPOSIT','MILESTONE','MANUAL','NO_PAYMENT_REQUIRED')),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SENT','VIEWED','PARTIALLY_PAID','PAID','OVERDUE','VOID','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
    subtotal_minor BIGINT NOT NULL CHECK (subtotal_minor >= 0),
    tax_minor BIGINT NOT NULL DEFAULT 0 CHECK (tax_minor >= 0),
    total_minor BIGINT NOT NULL CHECK (total_minor >= 0),
    required_amount_minor BIGINT NOT NULL CHECK (required_amount_minor >= 0),
    amount_paid_minor BIGINT NOT NULL DEFAULT 0 CHECK (amount_paid_minor >= 0),
    amount_refunded_minor BIGINT NOT NULL DEFAULT 0 CHECK (amount_refunded_minor >= 0),
    balance_due_minor BIGINT NOT NULL CHECK (balance_due_minor >= 0),
    memo VARCHAR(2000) NULL,
    due_at TIMESTAMPTZ NULL,
    sent_at TIMESTAMPTZ NULL,
    viewed_at TIMESTAMPTZ NULL,
    paid_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    creation_idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_by_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' CHECK (updated_by_type IN ('INTERNAL','CLIENT','PROVIDER','SYSTEM')),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_invoices_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_invoices_org_id_project UNIQUE (organization_id, id, project_id),
    CONSTRAINT ux_invoices_org_number UNIQUE (organization_id, invoice_number),
    CONSTRAINT ux_invoices_creation_request UNIQUE (organization_id, creation_idempotency_key),
    CONSTRAINT fk_invoices_project FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id),
    CONSTRAINT fk_invoices_client FOREIGN KEY (organization_id, client_id)
        REFERENCES client_onboarding.clients(organization_id, id),
    CONSTRAINT fk_invoices_project_client FOREIGN KEY (organization_id, project_id, client_id)
        REFERENCES client_onboarding.projects(organization_id, id, client_id),
    CONSTRAINT fk_invoices_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_invoices_onboarding_step FOREIGN KEY (organization_id, onboarding_id, step_instance_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_invoices_creator_tenant FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_invoices_updater CHECK (((updated_by_type IN ('INTERNAL','CLIENT')) AND updated_by IS NOT NULL) OR ((updated_by_type IN ('PROVIDER','SYSTEM')) AND updated_by IS NULL)),
    CONSTRAINT ck_invoices_step_pair CHECK ((onboarding_id IS NULL) = (step_instance_id IS NULL)),
    CONSTRAINT ck_invoices_amounts CHECK (
        total_minor = subtotal_minor + tax_minor
        AND required_amount_minor <= total_minor
        AND amount_refunded_minor <= amount_paid_minor
        AND balance_due_minor = GREATEST(0::bigint, total_minor - GREATEST(0::bigint, amount_paid_minor - amount_refunded_minor))
        AND (payment_policy <> 'NO_PAYMENT_REQUIRED' OR (total_minor = 0 AND required_amount_minor = 0))
    )
);
CREATE INDEX ix_invoices_org_status_created ON client_onboarding.invoices (organization_id, status, created_at DESC, id DESC);
CREATE INDEX ix_invoices_project_status ON client_onboarding.invoices (organization_id, project_id, status, created_at DESC, id DESC);
CREATE INDEX ix_invoices_step ON client_onboarding.invoices (organization_id, step_instance_id, created_at DESC, id DESC) WHERE step_instance_id IS NOT NULL;
CREATE INDEX ix_invoices_due ON client_onboarding.invoices (organization_id, due_at, status) WHERE due_at IS NOT NULL;

CREATE TABLE client_onboarding.invoice_items (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_amount_minor BIGINT NOT NULL CHECK (unit_amount_minor >= 0),
    line_total_minor BIGINT NOT NULL CHECK (line_total_minor >= 0),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ux_invoice_items_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_invoice_items_order UNIQUE (organization_id, invoice_id, display_order),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (organization_id, invoice_id)
        REFERENCES client_onboarding.invoices(organization_id, id),
    CONSTRAINT fk_invoice_items_creator_tenant FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_invoice_items_invoice ON client_onboarding.invoice_items (organization_id, invoice_id, display_order, id);

CREATE TABLE client_onboarding.payments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    project_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(180) NULL,
    provider_session_id VARCHAR(180) NULL,
    checkout_url VARCHAR(2000) NULL,
    session_expires_at TIMESTAMPTZ NULL,
    requested_amount_minor BIGINT NOT NULL CHECK (requested_amount_minor > 0),
    captured_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (captured_amount_minor >= 0),
    refunded_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (refunded_amount_minor >= 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(32) NOT NULL DEFAULT 'INITIATED'
        CHECK (status IN ('INITIATED','PENDING','AUTHORIZED','CAPTURED','FAILED','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
    session_idempotency_key VARCHAR(128) NOT NULL,
    failure_reason VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_payments_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_payments_invoice_id UNIQUE (organization_id, invoice_id, id),
    CONSTRAINT ux_payments_session_request UNIQUE (organization_id, invoice_id, session_idempotency_key),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (organization_id, invoice_id)
        REFERENCES client_onboarding.invoices(organization_id, id),
    CONSTRAINT fk_payments_invoice_project FOREIGN KEY (organization_id, invoice_id, project_id)
        REFERENCES client_onboarding.invoices(organization_id, id, project_id),
    CONSTRAINT ck_payments_amounts CHECK (captured_amount_minor <= requested_amount_minor AND refunded_amount_minor <= captured_amount_minor),
    CONSTRAINT ck_payments_session_fields CHECK (
        provider = 'MANUAL'
        OR (provider_session_id IS NULL AND checkout_url IS NULL AND session_expires_at IS NULL AND status IN ('INITIATED','FAILED','CANCELLED'))
        OR (provider_session_id IS NOT NULL AND checkout_url IS NOT NULL AND session_expires_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_payments_provider_payment ON client_onboarding.payments (provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE UNIQUE INDEX ux_payments_provider_session ON client_onboarding.payments (provider, provider_session_id) WHERE provider_session_id IS NOT NULL;
CREATE INDEX ix_payments_invoice_created ON client_onboarding.payments (organization_id, invoice_id, created_at DESC, id DESC);
CREATE INDEX ix_payments_status ON client_onboarding.payments (organization_id, status, updated_at DESC, id DESC);

CREATE TABLE client_onboarding.payment_transactions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(180) NULL,
    provider_transaction_id VARCHAR(180) NULL,
    transaction_type VARCHAR(32) NOT NULL
        CHECK (transaction_type IN ('INITIATED','PENDING','AUTHORIZED','CAPTURE','FAILURE','CANCELLATION','REFUND','MANUAL_CAPTURE','MANUAL_REFUND')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    reason VARCHAR(2000) NULL,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('PROVIDER','INTERNAL','SYSTEM')),
    actor_user_id UUID NULL REFERENCES client_onboarding.users(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_payment_transactions_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_payment_transactions_payment FOREIGN KEY (organization_id, invoice_id, payment_id)
        REFERENCES client_onboarding.payments(organization_id, invoice_id, id),
    CONSTRAINT ck_payment_transactions_actor CHECK ((actor_type='INTERNAL' AND actor_user_id IS NOT NULL) OR (actor_type<>'INTERNAL' AND actor_user_id IS NULL))
);
CREATE UNIQUE INDEX ux_payment_transactions_provider_event ON client_onboarding.payment_transactions (provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;
CREATE INDEX ix_payment_transactions_invoice ON client_onboarding.payment_transactions (organization_id, invoice_id, occurred_at, id);
CREATE INDEX ix_payment_transactions_payment ON client_onboarding.payment_transactions (organization_id, payment_id, occurred_at, id);

CREATE TABLE client_onboarding.refunds (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_refund_id VARCHAR(180) NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CAPTURED','FAILED','CANCELLED')),
    reason VARCHAR(2000) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    failure_reason VARCHAR(1000) NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_refunds_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_refunds_request UNIQUE (organization_id, payment_id, idempotency_key),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (organization_id, invoice_id, payment_id)
        REFERENCES client_onboarding.payments(organization_id, invoice_id, id),
    CONSTRAINT fk_refunds_creator_tenant FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE UNIQUE INDEX ux_refunds_provider_refund ON client_onboarding.refunds (provider, provider_refund_id) WHERE provider_refund_id IS NOT NULL;
CREATE INDEX ix_refunds_invoice ON client_onboarding.refunds (organization_id, invoice_id, requested_at DESC, id DESC);

CREATE TABLE client_onboarding.webhook_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(180) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_hash CHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ NULL,
    processing_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED' CHECK (processing_status IN ('RECEIVED','PROCESSED','IGNORED','FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error VARCHAR(2000) NULL,
    CONSTRAINT ux_webhook_events_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT ux_webhook_events_org_id UNIQUE (organization_id, id)
);
CREATE INDEX ix_webhook_events_org_received ON client_onboarding.webhook_events (organization_id, received_at DESC, id DESC);
CREATE INDEX ix_webhook_events_failure ON client_onboarding.webhook_events (processing_status, received_at, id) WHERE processing_status='FAILED';


-- Invoice updates may be internal-user, provider-webhook or system scheduled work.
CREATE OR REPLACE FUNCTION client_onboarding.validate_invoice_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.updated_by_type='INTERNAL' THEN
        IF NEW.updated_by IS NULL OR NOT EXISTS (
            SELECT 1 FROM client_onboarding.organization_users ou
            WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
        ) THEN
            RAISE EXCEPTION 'invoice updater is not an active internal organization user';
        END IF;
    ELSIF NEW.updated_by_type='CLIENT' THEN
        IF NEW.updated_by IS NULL OR NOT EXISTS (
            SELECT 1 FROM client_onboarding.client_users cu
            WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.updated_by AND cu.status='ACTIVE'
        ) THEN
            RAISE EXCEPTION 'invoice updater is not an active client user';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_invoices_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, updated_by, updated_by_type ON client_onboarding.invoices
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_invoice_actor();

-- Client/internal payment-session creators must be active identities in the same organization.
CREATE OR REPLACE FUNCTION client_onboarding.validate_payment_creator()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM client_onboarding.users u WHERE u.id=NEW.created_by AND u.status='ACTIVE') THEN
        RAISE EXCEPTION 'payment creator is not an active identity';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM client_onboarding.organization_users ou WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE')
       AND NOT EXISTS (SELECT 1 FROM client_onboarding.client_users cu WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.created_by AND cu.status='ACTIVE') THEN
        RAISE EXCEPTION 'payment creator is not active in organization';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payments_validate_creator
BEFORE INSERT OR UPDATE OF organization_id, created_by ON client_onboarding.payments
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_payment_creator();


CREATE OR REPLACE FUNCTION client_onboarding.validate_payment_transaction_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.actor_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.actor_user_id AND ou.status='ACTIVE'
    ) THEN
        RAISE EXCEPTION 'payment transaction actor is not an active internal organization user';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_transactions_validate_actor
BEFORE INSERT ON client_onboarding.payment_transactions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_payment_transaction_actor();

-- Provider ledger rows are immutable facts. Corrections are represented as new rows, never rewrites.
CREATE OR REPLACE FUNCTION client_onboarding.protect_payment_transaction()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payment transactions are immutable';
END;
$$;
CREATE TRIGGER trg_payment_transactions_immutable
BEFORE UPDATE OR DELETE ON client_onboarding.payment_transactions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_payment_transaction();

-- Once an invoice leaves DRAFT, its commercial definition and line items are immutable.
CREATE OR REPLACE FUNCTION client_onboarding.protect_sent_invoice_definition()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND (
        NEW.project_id <> OLD.project_id OR NEW.client_id <> OLD.client_id OR
        NEW.onboarding_id IS DISTINCT FROM OLD.onboarding_id OR NEW.step_instance_id IS DISTINCT FROM OLD.step_instance_id OR
        NEW.invoice_number <> OLD.invoice_number OR NEW.payment_policy <> OLD.payment_policy OR NEW.currency <> OLD.currency OR
        NEW.subtotal_minor <> OLD.subtotal_minor OR NEW.tax_minor <> OLD.tax_minor OR NEW.total_minor <> OLD.total_minor OR
        NEW.required_amount_minor <> OLD.required_amount_minor OR NEW.memo IS DISTINCT FROM OLD.memo OR
        NEW.due_at IS DISTINCT FROM OLD.due_at OR NEW.creation_idempotency_key <> OLD.creation_idempotency_key
    ) THEN
        RAISE EXCEPTION 'sent invoice commercial definition is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_invoices_protect_definition
BEFORE UPDATE ON client_onboarding.invoices
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_sent_invoice_definition();

CREATE OR REPLACE FUNCTION client_onboarding.protect_sent_invoice_items()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_invoice UUID; v_org UUID; v_status VARCHAR(32);
BEGIN
    v_invoice := COALESCE(NEW.invoice_id, OLD.invoice_id);
    v_org := COALESCE(NEW.organization_id, OLD.organization_id);
    SELECT status INTO v_status FROM client_onboarding.invoices WHERE organization_id=v_org AND id=v_invoice;
    IF v_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'sent invoice items are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;
CREATE TRIGGER trg_invoice_items_protect_sent
BEFORE INSERT OR UPDATE OR DELETE ON client_onboarding.invoice_items
FOR EACH ROW EXECUTE FUNCTION client_onboarding.protect_sent_invoice_items();
