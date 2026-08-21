-- Phase 4: secure project-specific client invitations, client-user project authorization,
-- client authentication sessions, and client portal support. Existing migrations remain immutable.

-- Client users remain separate from client contacts and internal organization memberships.
-- Additional composite keys let Phase 4 relationships prove tenant/client consistency in the database.
ALTER TABLE client_onboarding.client_users
    ADD CONSTRAINT ux_client_users_org_id_user UNIQUE (organization_id, id, user_id),
    ADD CONSTRAINT ux_client_users_org_id_client UNIQUE (organization_id, id, client_id);

ALTER TABLE client_onboarding.client_contacts
    ADD CONSTRAINT ux_client_contacts_org_client_id UNIQUE (organization_id, client_id, id);

ALTER TABLE client_onboarding.projects
    ADD CONSTRAINT ux_projects_org_id_client UNIQUE (organization_id, id, client_id);

ALTER TABLE client_onboarding.client_users
    ADD CONSTRAINT fk_client_users_created_by_tenant
        FOREIGN KEY (organization_id, created_by) REFERENCES client_onboarding.organization_users(organization_id, user_id),
    ADD CONSTRAINT fk_client_users_updated_by_tenant
        FOREIGN KEY (organization_id, updated_by) REFERENCES client_onboarding.organization_users(organization_id, user_id);

CREATE TABLE client_onboarding.client_user_projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    client_id UUID NOT NULL,
    client_user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    access_level VARCHAR(24) NOT NULL
        CHECK (access_level IN ('CLIENT_ADMIN', 'CLIENT_MEMBER')),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_client_user_projects_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_client_user_projects_access UNIQUE (organization_id, client_user_id, project_id),
    CONSTRAINT fk_client_user_projects_created_by_tenant
        FOREIGN KEY (organization_id, created_by) REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_client_user_projects_updated_by_tenant
        FOREIGN KEY (organization_id, updated_by) REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_client_user_projects_client_user_tenant_client
        FOREIGN KEY (organization_id, client_user_id, client_id)
        REFERENCES client_onboarding.client_users(organization_id, id, client_id),
    CONSTRAINT fk_client_user_projects_project_tenant_client
        FOREIGN KEY (organization_id, project_id, client_id)
        REFERENCES client_onboarding.projects(organization_id, id, client_id)
);
CREATE INDEX ix_client_user_projects_user_active
    ON client_onboarding.client_user_projects (organization_id, client_user_id, status, project_id);
CREATE INDEX ix_client_user_projects_project_active
    ON client_onboarding.client_user_projects (organization_id, project_id, status, client_user_id);

CREATE TABLE client_onboarding.client_invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    project_id UUID NOT NULL,
    client_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    display_name_snapshot VARCHAR(160) NOT NULL,
    access_level VARCHAR(24) NOT NULL
        CHECK (access_level IN ('CLIENT_ADMIN', 'CLIENT_MEMBER')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL,
    resend_count INTEGER NOT NULL DEFAULT 0 CHECK (resend_count >= 0),
    invited_by UUID NOT NULL,
    accepted_by_user_id UUID NULL REFERENCES client_onboarding.users(id),
    accepted_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_client_invitations_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_client_invitations_onboarding_project_tenant
        FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_client_invitations_project_client_tenant
        FOREIGN KEY (organization_id, project_id, client_id)
        REFERENCES client_onboarding.projects(organization_id, id, client_id),
    CONSTRAINT fk_client_invitations_contact_client_tenant
        FOREIGN KEY (organization_id, client_id, contact_id)
        REFERENCES client_onboarding.client_contacts(organization_id, client_id, id),
    CONSTRAINT fk_client_invitations_inviter_tenant
        FOREIGN KEY (organization_id, invited_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_client_invitations_accepted_user_tenant_client
        FOREIGN KEY (organization_id, client_id, accepted_by_user_id)
        REFERENCES client_onboarding.client_users(organization_id, client_id, user_id),
    CONSTRAINT ck_client_invitations_terminal_fields CHECK (
        (status = 'ACCEPTED' AND accepted_by_user_id IS NOT NULL AND accepted_at IS NOT NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND accepted_by_user_id IS NULL AND accepted_at IS NULL AND revoked_at IS NOT NULL)
        OR (status IN ('PENDING', 'EXPIRED') AND accepted_by_user_id IS NULL AND accepted_at IS NULL AND revoked_at IS NULL)
    )
);
CREATE UNIQUE INDEX ux_client_invitations_pending_contact
    ON client_onboarding.client_invitations (organization_id, onboarding_id, contact_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_client_invitations_onboarding_status
    ON client_onboarding.client_invitations (organization_id, onboarding_id, status, created_at DESC, id DESC);
CREATE INDEX ix_client_invitations_contact
    ON client_onboarding.client_invitations (organization_id, client_id, contact_id, created_at DESC);
CREATE INDEX ix_client_invitations_expiry
    ON client_onboarding.client_invitations (status, expires_at)
    WHERE status = 'PENDING';

-- Client portal sessions are deliberately separate from internal staff sessions. This prevents an
-- ambient client refresh cookie or client JWT from ever being interpreted as an organization membership.
CREATE TABLE client_onboarding.client_auth_sessions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
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
    CONSTRAINT ux_client_auth_sessions_org_id_user UNIQUE (organization_id, id, user_id),
    CONSTRAINT fk_client_auth_sessions_rotation_tenant_user
        FOREIGN KEY (organization_id, rotated_to_session_id, user_id)
        REFERENCES client_onboarding.client_auth_sessions(organization_id, id, user_id)
);
CREATE INDEX ix_client_auth_sessions_user_active
    ON client_onboarding.client_auth_sessions (user_id, organization_id, expires_at)
    WHERE revoked_at IS NULL;

-- Client-only short-lived security tokens keep password reset / MFA challenge state separate from
-- internal organization-membership security tokens.
CREATE TABLE client_onboarding.client_security_tokens (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    token_type VARCHAR(40) NOT NULL CHECK (token_type IN ('PASSWORD_RESET', 'MFA_LOGIN')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_client_security_tokens_user_type
    ON client_onboarding.client_security_tokens (organization_id, user_id, token_type, expires_at DESC);

-- Database guards ensure a client session/token can only be created for an identity that is actually
-- linked to at least one client in that tenant. This is a second line of defence behind application checks.
CREATE OR REPLACE FUNCTION client_onboarding.require_client_identity_tenant_membership()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id = NEW.organization_id
          AND cu.user_id = NEW.user_id
          AND cu.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'client identity is not active in organization';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_client_auth_sessions_require_access
BEFORE INSERT OR UPDATE OF organization_id, user_id ON client_onboarding.client_auth_sessions
FOR EACH ROW EXECUTE FUNCTION client_onboarding.require_client_identity_tenant_membership();

CREATE TRIGGER trg_client_security_tokens_require_access
BEFORE INSERT OR UPDATE OF organization_id, user_id ON client_onboarding.client_security_tokens
FOR EACH ROW EXECUTE FUNCTION client_onboarding.require_client_identity_tenant_membership();

-- Invitation acceptance is the first workflow transition performed by an authenticated client user.
-- Keep updated_by truthful while still proving that the updater belongs to the same tenant either as
-- an internal organization member or as an active client user. created_by remains internal-only.
ALTER TABLE client_onboarding.onboarding_instances
    DROP CONSTRAINT fk_onboarding_instances_updater_tenant,
    ADD CONSTRAINT fk_onboarding_instances_updated_by_user
        FOREIGN KEY (updated_by) REFERENCES client_onboarding.users(id);

CREATE OR REPLACE FUNCTION client_onboarding.validate_onboarding_instance_updater()
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
        RAISE EXCEPTION 'onboarding updater is not active in organization';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_onboarding_instances_validate_updater
BEFORE INSERT OR UPDATE OF organization_id, updated_by ON client_onboarding.onboarding_instances
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_onboarding_instance_updater();

-- Audit/activity actors can now be either internal organization users or external client users.
-- The global user id is retained for traceability while a trigger enforces tenant membership by actor type.
ALTER TABLE client_onboarding.audit_logs
    DROP CONSTRAINT IF EXISTS fk_audit_logs_actor_tenant;
ALTER TABLE client_onboarding.audit_logs
    ADD COLUMN actor_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'
        CHECK (actor_type IN ('INTERNAL', 'CLIENT', 'SYSTEM')),
    ADD CONSTRAINT fk_audit_logs_actor_user FOREIGN KEY (actor_user_id) REFERENCES client_onboarding.users(id),
    ADD CONSTRAINT ck_audit_logs_actor_presence CHECK (
        (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        OR (actor_type IN ('INTERNAL', 'CLIENT') AND actor_user_id IS NOT NULL)
    );

ALTER TABLE client_onboarding.activity_logs
    DROP CONSTRAINT IF EXISTS fk_activity_logs_actor_tenant;
ALTER TABLE client_onboarding.activity_logs
    ADD COLUMN actor_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'
        CHECK (actor_type IN ('INTERNAL', 'CLIENT', 'SYSTEM')),
    ADD CONSTRAINT fk_activity_logs_actor_user FOREIGN KEY (actor_user_id) REFERENCES client_onboarding.users(id),
    ADD CONSTRAINT ck_activity_logs_actor_presence CHECK (
        (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        OR (actor_type IN ('INTERNAL', 'CLIENT') AND actor_user_id IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION client_onboarding.validate_tenant_actor()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.actor_type = 'SYSTEM' THEN
        RETURN NEW;
    ELSIF NEW.actor_type = 'INTERNAL' THEN
        IF NOT EXISTS (
            SELECT 1 FROM client_onboarding.organization_users ou
            WHERE ou.organization_id = NEW.organization_id AND ou.user_id = NEW.actor_user_id AND ou.status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'internal audit/activity actor is not a member of organization';
        END IF;
    ELSIF NEW.actor_type = 'CLIENT' THEN
        IF NOT EXISTS (
            SELECT 1 FROM client_onboarding.client_users cu
            WHERE cu.organization_id = NEW.organization_id AND cu.user_id = NEW.actor_user_id AND cu.status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'client audit/activity actor is not linked to organization';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_logs_validate_actor
BEFORE INSERT ON client_onboarding.audit_logs
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_tenant_actor();

CREATE TRIGGER trg_activity_logs_validate_actor
BEFORE INSERT ON client_onboarding.activity_logs
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_tenant_actor();
