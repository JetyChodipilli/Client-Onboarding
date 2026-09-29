INSERT INTO permissions(id,code,description,created_at) VALUES
 ('00000000-0000-0000-0000-000000000036','INVOICE_READ','Read invoices and payment history',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000037','PAYMENT_RECONCILE','Reconcile provider payments',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000038','PAYMENT_REFUND','Request and reconcile refunds',CURRENT_TIMESTAMP);

CREATE TABLE invoices (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
 project_id uuid NOT NULL, step_id uuid NOT NULL, invoice_number varchar(80) NOT NULL,
 currency varchar(3) NOT NULL CHECK(currency='INR'),
 policy varchar(24) NOT NULL CHECK(policy IN ('FULL','DEPOSIT','MILESTONE','MANUAL','NO_PAYMENT_REQUIRED')),
 subtotal_minor bigint NOT NULL CHECK(subtotal_minor BETWEEN 0 AND 100000000000),
 tax_minor bigint NOT NULL CHECK(tax_minor BETWEEN 0 AND 100000000000),
 total_minor bigint NOT NULL CHECK(total_minor BETWEEN 0 AND 100000000000),
 threshold_minor bigint NOT NULL CHECK(threshold_minor BETWEEN 0 AND total_minor),
 captured_minor bigint NOT NULL DEFAULT 0 CHECK(captured_minor >= 0),
 refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor BETWEEN 0 AND captured_minor),
 reserved_minor bigint NOT NULL DEFAULT 0 CHECK(reserved_minor BETWEEN 0 AND total_minor),
 status varchar(24) NOT NULL CHECK(status IN ('DRAFT','SENT','VIEWED','PARTIALLY_PAID','PAID','OVERDUE','VOID','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
 due_date date NOT NULL, note varchar(2000), sent_at timestamptz, viewed_at timestamptz,
 idempotency_key varchar(120) NOT NULL, request_hash varchar(64) NOT NULL,
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,invoice_number), UNIQUE(organization_id,idempotency_key),
 FOREIGN KEY(organization_id,project_id) REFERENCES projects(organization_id,id),
 FOREIGN KEY(organization_id,step_id) REFERENCES onboarding_step_instances(organization_id,id),
 CHECK(total_minor=subtotal_minor+tax_minor),
 CHECK(captured_minor-refunded_minor+reserved_minor<=total_minor)
);
CREATE UNIQUE INDEX uq_invoice_active_step ON invoices(organization_id,step_id) WHERE status NOT IN ('VOID','CANCELLED');
CREATE INDEX ix_invoice_list ON invoices(organization_id,created_at DESC,id);
CREATE INDEX ix_invoice_project ON invoices(organization_id,project_id,created_at DESC,id);
CREATE TABLE invoice_items (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, invoice_id uuid NOT NULL,
 position integer NOT NULL CHECK(position BETWEEN 0 AND 49), description varchar(500) NOT NULL,
 quantity integer NOT NULL CHECK(quantity BETWEEN 1 AND 10000), unit_amount_minor bigint NOT NULL CHECK(unit_amount_minor BETWEEN 0 AND 100000000000),
 tax_basis_points integer NOT NULL CHECK(tax_basis_points BETWEEN 0 AND 10000),
 subtotal_minor bigint NOT NULL, tax_minor bigint NOT NULL, total_minor bigint NOT NULL,
 UNIQUE(organization_id,invoice_id,position),
 FOREIGN KEY(organization_id,invoice_id) REFERENCES invoices(organization_id,id),
 CHECK(subtotal_minor=quantity*unit_amount_minor AND total_minor=subtotal_minor+tax_minor)
);
CREATE TABLE payments (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, invoice_id uuid NOT NULL,
 provider varchar(16) NOT NULL CHECK(provider='RAZORPAY'), provider_order_id varchar(100),
 amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 100000000000), currency varchar(3) NOT NULL CHECK(currency='INR'),
 status varchar(16) NOT NULL CHECK(status IN ('CREATING','READY','UNKNOWN','SETTLED','FAILED')),
 idempotency_key varchar(120) NOT NULL, request_hash varchar(64) NOT NULL,
 created_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,invoice_id,id), UNIQUE(organization_id,idempotency_key),
 UNIQUE(organization_id,provider,provider_order_id), FOREIGN KEY(organization_id,invoice_id) REFERENCES invoices(organization_id,id)
);
CREATE UNIQUE INDEX uq_payment_open_invoice ON payments(organization_id,invoice_id) WHERE status IN ('CREATING','READY','UNKNOWN');
CREATE TABLE payment_transactions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, invoice_id uuid NOT NULL, payment_id uuid,
 provider varchar(16) NOT NULL CHECK(provider IN ('RAZORPAY','MANUAL')), provider_payment_id varchar(100),
 status varchar(24) NOT NULL CHECK(status IN ('INITIATED','PENDING','AUTHORIZED','CAPTURED','FAILED','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
 amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 1 AND 100000000000),
 refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor BETWEEN 0 AND amount_minor), currency varchar(3) NOT NULL CHECK(currency='INR'),
 reference varchar(120), reason varchar(2000), idempotency_key varchar(120), request_hash varchar(64),
 created_at timestamptz NOT NULL, created_by uuid REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,provider,provider_payment_id), UNIQUE(organization_id,reference), UNIQUE(organization_id,idempotency_key),
 FOREIGN KEY(organization_id,invoice_id) REFERENCES invoices(organization_id,id),
 FOREIGN KEY(organization_id,invoice_id,payment_id) REFERENCES payments(organization_id,invoice_id,id),
 CHECK((provider='RAZORPAY' AND payment_id IS NOT NULL AND provider_payment_id IS NOT NULL)
    OR (provider='MANUAL' AND payment_id IS NULL AND reference IS NOT NULL AND reason IS NOT NULL))
);
CREATE INDEX ix_payment_transaction_invoice ON payment_transactions(organization_id,invoice_id,created_at DESC,id);
CREATE TABLE refunds (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, transaction_id uuid NOT NULL,
 provider_refund_id varchar(100), amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 1 AND 100000000000),
 status varchar(16) NOT NULL CHECK(status IN ('CREATING','PENDING','UNKNOWN','PROCESSED','FAILED')),
 reason varchar(2000) NOT NULL, idempotency_key varchar(120) NOT NULL, request_hash varchar(64) NOT NULL,
 created_at timestamptz NOT NULL, created_by uuid REFERENCES users(id),
 updated_at timestamptz NOT NULL, updated_by uuid REFERENCES users(id), version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), UNIQUE(organization_id,provider_refund_id), UNIQUE(organization_id,idempotency_key),
 FOREIGN KEY(organization_id,transaction_id) REFERENCES payment_transactions(organization_id,id)
);
CREATE INDEX ix_refund_transaction ON refunds(organization_id,transaction_id,created_at DESC,id);
CREATE TABLE payment_webhook_events (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
 provider varchar(16) NOT NULL CHECK(provider='RAZORPAY'), provider_event_id varchar(160) NOT NULL,
 event_type varchar(80) NOT NULL, payload_hash varchar(64) NOT NULL,
 processing_status varchar(16) NOT NULL CHECK(processing_status IN ('PROCESSED','IGNORED')),
 received_at timestamptz NOT NULL, processed_at timestamptz NOT NULL,
 UNIQUE(organization_id,provider,provider_event_id)
);
CREATE TABLE billing_outbox_events (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, invoice_id uuid NOT NULL,
 event_type varchar(60) NOT NULL, occurred_at timestamptz NOT NULL, correlation_id varchar(36) NOT NULL,
 payload_version integer NOT NULL DEFAULT 1, processed_at timestamptz,
 FOREIGN KEY(organization_id,invoice_id) REFERENCES invoices(organization_id,id)
);
CREATE INDEX ix_billing_outbox_pending ON billing_outbox_events(occurred_at,id) WHERE processed_at IS NULL;

CREATE FUNCTION guard_invoice() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Invoices are retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id,NEW.organization_id,NEW.project_id,NEW.step_id,NEW.invoice_number,NEW.currency,NEW.policy,
 NEW.subtotal_minor,NEW.tax_minor,NEW.total_minor,NEW.threshold_minor,NEW.due_date,NEW.note,NEW.idempotency_key,NEW.request_hash,NEW.created_at,NEW.created_by)
 IS DISTINCT FROM ROW(OLD.id,OLD.organization_id,OLD.project_id,OLD.step_id,OLD.invoice_number,OLD.currency,OLD.policy,
 OLD.subtotal_minor,OLD.tax_minor,OLD.total_minor,OLD.threshold_minor,OLD.due_date,OLD.note,OLD.idempotency_key,OLD.request_hash,OLD.created_at,OLD.created_by)
 THEN RAISE EXCEPTION 'Invoice content is immutable; void and replace a draft' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_invoice_content BEFORE UPDATE OR DELETE ON invoices FOR EACH ROW EXECUTE FUNCTION guard_invoice();
CREATE FUNCTION guard_billing_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Financial evidence is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_invoice_item BEFORE UPDATE OR DELETE ON invoice_items FOR EACH ROW EXECUTE FUNCTION guard_billing_append_only();
CREATE TRIGGER immutable_payment_event BEFORE UPDATE OR DELETE ON payment_webhook_events FOR EACH ROW EXECUTE FUNCTION guard_billing_append_only();
