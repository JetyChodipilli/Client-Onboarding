CREATE TABLE permissions (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    description varchar(240) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE organizations (
    id uuid PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    display_name varchar(160) NOT NULL,
    password_hash varchar(100) NOT NULL,
    principal_type varchar(24) NOT NULL CHECK (principal_type IN ('INTERNAL', 'CLIENT')),
    status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    email_verified_at timestamptz,
    failed_login_count integer NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until timestamptz,
    credential_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE roles (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    name varchar(100) NOT NULL,
    description varchar(240) NOT NULL,
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, name),
    UNIQUE (organization_id, id)
);

CREATE TABLE role_permissions (
    role_id uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES permissions(id),
    created_at timestamptz NOT NULL,
    created_by uuid,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE organization_users (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    role_id uuid NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    invited_at timestamptz NOT NULL,
    joined_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, user_id),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, role_id) REFERENCES roles(organization_id, id)
);

CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash char(64) NOT NULL UNIQUE,
    credential_version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    ip_hash char(64),
    user_agent_hash char(64)
);

CREATE TABLE email_verification_tokens (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE TABLE password_reset_tokens (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE TABLE organization_invitation_tokens (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    membership_id uuid NOT NULL,
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_users(organization_id, id)
);

CREATE TABLE mfa_methods (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users(id),
    encrypted_secret varchar(500) NOT NULL,
    key_version integer NOT NULL DEFAULT 1,
    enrolled_at timestamptz NOT NULL,
    enabled_at timestamptz NOT NULL,
    last_used_step bigint
);

CREATE TABLE mfa_recovery_codes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    code_hash char(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    consumed_at timestamptz
);

CREATE TABLE auth_challenges (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash char(64) NOT NULL UNIQUE,
    purpose varchar(32) NOT NULL CHECK (purpose IN ('MFA_VERIFY', 'MFA_ENROLL')),
    encrypted_secret varchar(500),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY,
    organization_id uuid REFERENCES organizations(id),
    actor_user_id uuid REFERENCES users(id),
    action varchar(100) NOT NULL,
    entity_type varchar(80) NOT NULL,
    entity_id uuid,
    source varchar(32) NOT NULL,
    before_state text,
    after_state text,
    request_id uuid,
    correlation_id uuid,
    ip_hash char(64),
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_organization_users_org_status ON organization_users (organization_id, status);
CREATE INDEX idx_organization_users_user_status ON organization_users (user_id, status);
CREATE INDEX idx_roles_org_active ON roles (organization_id, archived_at);
CREATE INDEX idx_auth_sessions_token_active ON auth_sessions (token_hash, revoked_at, expires_at);
CREATE INDEX idx_auth_sessions_user ON auth_sessions (user_id, revoked_at);
CREATE INDEX idx_verification_token_active ON email_verification_tokens (token_hash, consumed_at, expires_at);
CREATE INDEX idx_password_reset_active ON password_reset_tokens (token_hash, consumed_at, expires_at);
CREATE INDEX idx_org_invitation_active ON organization_invitation_tokens (token_hash, consumed_at, expires_at);
CREATE INDEX idx_auth_challenge_active ON auth_challenges (token_hash, consumed_at, expires_at);
CREATE INDEX idx_audit_org_created ON audit_logs (organization_id, created_at DESC);

INSERT INTO permissions (id, code, description, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ORGANIZATION_READ', 'Read the current organization profile', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'ORGANIZATION_UPDATE', 'Update the current organization profile', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'USER_MANAGE', 'Invite and manage organization members', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000004', 'ROLE_MANAGE', 'Create and manage organization roles', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000005', 'AUDIT_READ', 'Read the organization security audit log', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000006', 'CLIENT_CREATE', 'Create client records', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000007', 'CLIENT_READ', 'Read client records', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000008', 'CLIENT_UPDATE', 'Update client records', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000009', 'PROJECT_CREATE', 'Create projects', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000010', 'PROJECT_UPDATE', 'Update projects', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000011', 'PROJECT_ACTIVATE', 'Activate ready projects', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000012', 'ONBOARDING_START', 'Start onboarding instances', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000013', 'ONBOARDING_REVIEW', 'Review onboarding submissions', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000014', 'ONBOARDING_APPROVE', 'Approve onboarding completion', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000015', 'INVOICE_CREATE', 'Create invoices', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000016', 'INVOICE_SEND', 'Send invoices', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000017', 'PAYMENT_OVERRIDE', 'Apply an authorized payment override', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000018', 'CONTRACT_CREATE', 'Create contracts', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000019', 'CONTRACT_SEND', 'Send contracts', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000020', 'ASSET_REVIEW', 'Review client assets', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000021', 'ACCESS_VERIFY', 'Verify external platform access', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000022', 'REPORT_READ', 'Read organization reports', CURRENT_TIMESTAMP);
