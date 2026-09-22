INSERT INTO permissions (id, code, description, created_at) VALUES
    ('00000000-0000-0000-0000-000000000027', 'ONBOARDING_INVITE',
     'Invite client users to an onboarding portal', CURRENT_TIMESTAMP);

ALTER TABLE client_users
    ADD COLUMN client_role varchar(24) NOT NULL DEFAULT 'MEMBER'
        CHECK (client_role IN ('ADMIN', 'MEMBER')),
    ADD COLUMN activated_at timestamptz,
    ADD CONSTRAINT uq_client_users_org_client_id UNIQUE (organization_id, client_id, id);

ALTER TABLE client_contacts
    ADD CONSTRAINT uq_client_contacts_org_client_id UNIQUE (organization_id, client_id, id);

ALTER TABLE projects
    ADD CONSTRAINT uq_projects_org_client_id UNIQUE (organization_id, client_id, id);

CREATE TABLE client_user_project_access (
    organization_id uuid NOT NULL,
    client_id uuid NOT NULL,
    client_user_id uuid NOT NULL,
    project_id uuid NOT NULL,
    granted_at timestamptz NOT NULL,
    granted_by uuid NOT NULL REFERENCES users(id),
    PRIMARY KEY (organization_id, client_user_id, project_id),
    FOREIGN KEY (organization_id, client_id, client_user_id)
        REFERENCES client_users(organization_id, client_id, id),
    FOREIGN KEY (organization_id, client_id, project_id)
        REFERENCES projects(organization_id, client_id, id)
);

CREATE TABLE client_invitations (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    client_id uuid NOT NULL,
    contact_id uuid NOT NULL,
    project_id uuid NOT NULL,
    onboarding_id uuid NOT NULL,
    invited_email varchar(254) NOT NULL,
    client_role varchar(24) NOT NULL CHECK (client_role IN ('ADMIN', 'MEMBER')),
    token_hash char(64) NOT NULL UNIQUE,
    status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    delivery_status varchar(24) NOT NULL CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    delivery_error varchar(500),
    expires_at timestamptz NOT NULL,
    sent_at timestamptz,
    accepted_at timestamptz,
    accepted_by uuid REFERENCES users(id),
    revoked_at timestamptz,
    resend_count integer NOT NULL DEFAULT 0 CHECK (resend_count >= 0),
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, client_id, contact_id)
        REFERENCES client_contacts(organization_id, client_id, id),
    FOREIGN KEY (organization_id, client_id, project_id)
        REFERENCES projects(organization_id, client_id, id),
    FOREIGN KEY (organization_id, onboarding_id)
        REFERENCES onboarding_instances(organization_id, id)
);

CREATE UNIQUE INDEX uq_pending_client_invitation
    ON client_invitations (organization_id, onboarding_id, contact_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_client_invitations_onboarding
    ON client_invitations (organization_id, onboarding_id, created_at DESC);
CREATE INDEX idx_client_invitations_token_active
    ON client_invitations (token_hash, status, expires_at);
CREATE INDEX idx_client_user_projects
    ON client_user_project_access (organization_id, client_user_id, project_id);
