-- Phase 1: Identity, authentication, RBAC, and tenant isolation.
-- Existing migrations are immutable; this migration only adds Phase 1 schema.

CREATE TABLE client_onboarding.organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_organizations_slug_lower
    ON client_onboarding.organizations (LOWER(slug));

CREATE TABLE client_onboarding.users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    email_verified_at TIMESTAMPTZ NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until TIMESTAMPTZ NULL,
    credentials_version BIGINT NOT NULL DEFAULT 0,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret_encrypted TEXT NULL,
    mfa_pending_secret_encrypted TEXT NULL,
    mfa_pending_created_at TIMESTAMPTZ NULL,
    last_login_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_users_normalized_email UNIQUE (normalized_email)
);

CREATE TABLE client_onboarding.organization_users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')),
    joined_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_organization_users_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ux_organization_users_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_organization_users_org_id_user UNIQUE (organization_id, id, user_id)
);
CREATE INDEX ix_organization_users_org_status
    ON client_onboarding.organization_users (organization_id, status);
CREATE INDEX ix_organization_users_user
    ON client_onboarding.organization_users (user_id);

CREATE TABLE client_onboarding.permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(60) NOT NULL,
    description VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE client_onboarding.roles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(240) NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_roles_org_code UNIQUE (organization_id, code),
    CONSTRAINT ux_roles_org_id UNIQUE (organization_id, id)
);
CREATE INDEX ix_roles_org_status ON client_onboarding.roles (organization_id, status);

CREATE TABLE client_onboarding.role_permissions (
    organization_id UUID NOT NULL,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL REFERENCES client_onboarding.permissions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role_tenant
        FOREIGN KEY (organization_id, role_id)
        REFERENCES client_onboarding.roles(organization_id, id)
        ON DELETE CASCADE
);
CREATE INDEX ix_role_permissions_org_role
    ON client_onboarding.role_permissions (organization_id, role_id);

CREATE TABLE client_onboarding.organization_user_roles (
    organization_id UUID NOT NULL,
    organization_user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    PRIMARY KEY (organization_user_id, role_id),
    CONSTRAINT fk_org_user_roles_membership_tenant
        FOREIGN KEY (organization_id, organization_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_org_user_roles_role_tenant
        FOREIGN KEY (organization_id, role_id)
        REFERENCES client_onboarding.roles(organization_id, id)
        ON DELETE CASCADE
);
CREATE INDEX ix_organization_user_roles_org_member
    ON client_onboarding.organization_user_roles (organization_id, organization_user_id);

CREATE TABLE client_onboarding.organization_invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    invited_by UUID NOT NULL REFERENCES client_onboarding.users(id),
    accepted_by_user_id UUID NULL REFERENCES client_onboarding.users(id),
    accepted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_org_invitations_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_org_invitations_inviter_tenant
        FOREIGN KEY (organization_id, invited_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_org_invitations_acceptor_tenant
        FOREIGN KEY (organization_id, accepted_by_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_org_invitations_org_status
    ON client_onboarding.organization_invitations (organization_id, status, created_at DESC);
CREATE INDEX ix_org_invitations_org_email
    ON client_onboarding.organization_invitations (organization_id, normalized_email);
CREATE UNIQUE INDEX ux_org_invitations_one_pending
    ON client_onboarding.organization_invitations (organization_id, normalized_email)
    WHERE status = 'PENDING';

CREATE TABLE client_onboarding.organization_invitation_roles (
    organization_id UUID NOT NULL,
    invitation_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (invitation_id, role_id),
    CONSTRAINT fk_invitation_roles_invitation_tenant
        FOREIGN KEY (organization_id, invitation_id)
        REFERENCES client_onboarding.organization_invitations(organization_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_invitation_roles_role_tenant
        FOREIGN KEY (organization_id, role_id)
        REFERENCES client_onboarding.roles(organization_id, id)
);

CREATE TABLE client_onboarding.auth_sessions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    organization_user_id UUID NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL UNIQUE,
    credentials_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    revoke_reason VARCHAR(80) NULL,
    rotated_to_session_id UUID NULL,
    last_used_at TIMESTAMPTZ NULL,
    ip_address VARCHAR(45) NULL,
    user_agent_hash CHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_auth_sessions_org_id_user UNIQUE (organization_id, id, user_id),
    CONSTRAINT fk_auth_sessions_membership_tenant
        FOREIGN KEY (organization_id, organization_user_id, user_id)
        REFERENCES client_onboarding.organization_users(organization_id, id, user_id),
    CONSTRAINT fk_auth_sessions_rotation_tenant_user
        FOREIGN KEY (organization_id, rotated_to_session_id, user_id)
        REFERENCES client_onboarding.auth_sessions(organization_id, id, user_id)
);
CREATE INDEX ix_auth_sessions_user_active
    ON client_onboarding.auth_sessions (user_id, organization_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_sessions_membership
    ON client_onboarding.auth_sessions (organization_id, organization_user_id);

CREATE TABLE client_onboarding.security_tokens (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    token_type VARCHAR(40) NOT NULL
        CHECK (token_type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION', 'MFA_LOGIN', 'MFA_SETUP')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_security_tokens_membership_tenant
        FOREIGN KEY (organization_id, user_id)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_security_tokens_user_type
    ON client_onboarding.security_tokens (organization_id, user_id, token_type, expires_at DESC);

CREATE TABLE client_onboarding.auth_rate_limits (
    key_hash CHAR(64) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    blocked_until TIMESTAMPTZ NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (key_hash, scope)
);
CREATE INDEX ix_auth_rate_limits_updated_at
    ON client_onboarding.auth_rate_limits (updated_at);

CREATE TABLE client_onboarding.audit_logs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    actor_user_id UUID NULL REFERENCES client_onboarding.users(id),
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NULL,
    source VARCHAR(40) NOT NULL,
    before_state JSONB NULL,
    after_state JSONB NULL,
    request_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    ip_address VARCHAR(45) NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_logs_org_time
    ON client_onboarding.audit_logs (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_org_entity
    ON client_onboarding.audit_logs (organization_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_org_actor
    ON client_onboarding.audit_logs (organization_id, actor_user_id, occurred_at DESC);

-- Application-level audit records are append-only. Retention/administrative deletion must use a
-- separately privileged database role rather than the application role.
CREATE OR REPLACE FUNCTION client_onboarding.prevent_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs are append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_no_update
BEFORE UPDATE OR DELETE ON client_onboarding.audit_logs
FOR EACH ROW EXECUTE FUNCTION client_onboarding.prevent_audit_log_mutation();

-- Canonical permission registry. Later phases add behavior behind these permissions without
-- changing Phase 1 authorization semantics.
INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000001', 'ORG_READ', 'ORGANIZATION', 'Read the current organization profile.'),
('00000000-0000-0000-0000-000000000002', 'ORG_UPDATE', 'ORGANIZATION', 'Update the current organization profile.'),
('00000000-0000-0000-0000-000000000003', 'USER_READ', 'IDENTITY', 'Read organization users and memberships.'),
('00000000-0000-0000-0000-000000000004', 'USER_MANAGE', 'IDENTITY', 'Invite, suspend, and manage organization users.'),
('00000000-0000-0000-0000-000000000005', 'ROLE_READ', 'IDENTITY', 'Read organization roles and permission mappings.'),
('00000000-0000-0000-0000-000000000006', 'ROLE_MANAGE', 'IDENTITY', 'Create and update organization roles and permission mappings.'),
('00000000-0000-0000-0000-000000000007', 'AUDIT_READ', 'AUDIT', 'Read organization audit records.'),
('00000000-0000-0000-0000-000000000008', 'CLIENT_CREATE', 'CLIENT', 'Create clients.'),
('00000000-0000-0000-0000-000000000009', 'CLIENT_READ', 'CLIENT', 'Read clients.'),
('00000000-0000-0000-0000-000000000010', 'CLIENT_UPDATE', 'CLIENT', 'Update clients.'),
('00000000-0000-0000-0000-000000000011', 'PROJECT_CREATE', 'PROJECT', 'Create projects.'),
('00000000-0000-0000-0000-000000000012', 'PROJECT_UPDATE', 'PROJECT', 'Update projects.'),
('00000000-0000-0000-0000-000000000013', 'PROJECT_ACTIVATE', 'PROJECT', 'Activate ready projects.'),
('00000000-0000-0000-0000-000000000014', 'ONBOARDING_START', 'ONBOARDING', 'Start onboarding.'),
('00000000-0000-0000-0000-000000000015', 'ONBOARDING_REVIEW', 'ONBOARDING', 'Review onboarding submissions.'),
('00000000-0000-0000-0000-000000000016', 'ONBOARDING_APPROVE', 'ONBOARDING', 'Approve onboarding.'),
('00000000-0000-0000-0000-000000000017', 'INVOICE_CREATE', 'BILLING', 'Create invoices.'),
('00000000-0000-0000-0000-000000000018', 'INVOICE_SEND', 'BILLING', 'Send invoices.'),
('00000000-0000-0000-0000-000000000019', 'PAYMENT_OVERRIDE', 'PAYMENTS', 'Perform controlled payment overrides.'),
('00000000-0000-0000-0000-000000000020', 'CONTRACT_CREATE', 'CONTRACTS', 'Create contracts.'),
('00000000-0000-0000-0000-000000000021', 'CONTRACT_SEND', 'CONTRACTS', 'Send contracts.'),
('00000000-0000-0000-0000-000000000022', 'ASSET_REVIEW', 'ASSETS', 'Review client assets.'),
('00000000-0000-0000-0000-000000000023', 'ACCESS_VERIFY', 'ACCESS', 'Verify third-party platform access.'),
('00000000-0000-0000-0000-000000000024', 'REPORT_READ', 'REPORTING', 'Read reports.');
