-- Phase 2: clients, contacts, service catalog, projects, project members, client-user link foundation,
-- and project activity timeline. This migration also extends the Phase 1 permission registry for the
-- read/manage capabilities introduced by Phase 2 without rewriting earlier migrations.

INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000025', 'SERVICE_READ', 'SERVICE_CATALOG', 'Read the organization service catalog.'),
('00000000-0000-0000-0000-000000000026', 'SERVICE_MANAGE', 'SERVICE_CATALOG', 'Create, update, and archive organization services.'),
('00000000-0000-0000-0000-000000000027', 'PROJECT_READ', 'PROJECT', 'Read organization projects and project activity.');

-- Existing organization-admin system roles should retain complete administrative capability when
-- a new product phase introduces permissions. Custom roles are deliberately not auto-expanded.
INSERT INTO client_onboarding.role_permissions
    (organization_id, role_id, permission_id, created_by)
SELECT r.organization_id, r.id, p.id, r.created_by
FROM client_onboarding.roles r
CROSS JOIN client_onboarding.permissions p
WHERE r.system_role = TRUE
  AND r.code = 'ORGANIZATION_ADMIN'
  AND r.status = 'ACTIVE'
  AND p.code IN ('SERVICE_READ', 'SERVICE_MANAGE', 'PROJECT_READ')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE client_onboarding.clients (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    name VARCHAR(180) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PROSPECT'
        CHECK (status IN ('PROSPECT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_clients_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_clients_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_clients_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_clients_org_status_created
    ON client_onboarding.clients (organization_id, status, created_at DESC, id DESC);
CREATE INDEX ix_clients_org_name
    ON client_onboarding.clients (organization_id, name, id);

CREATE TABLE client_onboarding.client_contacts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    client_id UUID NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    job_title VARCHAR(120) NULL,
    phone VARCHAR(40) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_client_contacts_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_client_contacts_client_email UNIQUE (organization_id, client_id, normalized_email),
    CONSTRAINT fk_client_contacts_client_tenant
        FOREIGN KEY (organization_id, client_id)
        REFERENCES client_onboarding.clients(organization_id, id),
    CONSTRAINT fk_client_contacts_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_client_contacts_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_client_contacts_client_name
    ON client_onboarding.client_contacts (organization_id, client_id, display_name, id);

CREATE TABLE client_onboarding.services (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_services_org_code UNIQUE (organization_id, code),
    CONSTRAINT ux_services_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_services_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_services_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_services_org_status_name
    ON client_onboarding.services (organization_id, status, name, id);

CREATE TABLE client_onboarding.projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    client_id UUID NOT NULL,
    service_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ONBOARDING', 'READY', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    hold_from_status VARCHAR(24) NULL
        CHECK (hold_from_status IS NULL OR hold_from_status IN ('ONBOARDING', 'READY', 'ACTIVE')),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_projects_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_projects_client_tenant
        FOREIGN KEY (organization_id, client_id)
        REFERENCES client_onboarding.clients(organization_id, id),
    CONSTRAINT fk_projects_service_tenant
        FOREIGN KEY (organization_id, service_id)
        REFERENCES client_onboarding.services(organization_id, id),
    CONSTRAINT fk_projects_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_projects_updater_tenant
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_projects_org_status_created
    ON client_onboarding.projects (organization_id, status, created_at DESC, id DESC);
CREATE INDEX ix_projects_org_client_created
    ON client_onboarding.projects (organization_id, client_id, created_at DESC, id DESC);
CREATE INDEX ix_projects_org_service
    ON client_onboarding.projects (organization_id, service_id, status);

CREATE TABLE client_onboarding.project_members (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    organization_user_id UUID NOT NULL,
    responsibility VARCHAR(120) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ux_project_members_project_member UNIQUE (organization_id, project_id, organization_user_id),
    CONSTRAINT ux_project_members_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_project_members_project_tenant
        FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_project_members_membership_tenant
        FOREIGN KEY (organization_id, organization_user_id)
        REFERENCES client_onboarding.organization_users(organization_id, id),
    CONSTRAINT fk_project_members_creator_tenant
        FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id)
);
CREATE INDEX ix_project_members_project
    ON client_onboarding.project_members (organization_id, project_id, created_at, id);
CREATE INDEX ix_project_members_member
    ON client_onboarding.project_members (organization_id, organization_user_id, project_id);

-- Client users are authenticated identities linked to an external client, not contacts and not
-- internal organization memberships. Phase 4 owns invitation/account activation behavior.
CREATE TABLE client_onboarding.client_users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    client_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_client_users_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_client_users_client_user UNIQUE (organization_id, client_id, user_id),
    CONSTRAINT fk_client_users_client_tenant
        FOREIGN KEY (organization_id, client_id)
        REFERENCES client_onboarding.clients(organization_id, id)
);
CREATE INDEX ix_client_users_user
    ON client_onboarding.client_users (user_id, organization_id, client_id);

CREATE TABLE client_onboarding.activity_logs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    client_id UUID NULL,
    project_id UUID NULL,
    actor_user_id UUID NULL REFERENCES client_onboarding.users(id),
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    summary VARCHAR(500) NOT NULL,
    metadata JSONB NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_logs_client_tenant
        FOREIGN KEY (organization_id, client_id)
        REFERENCES client_onboarding.clients(organization_id, id),
    CONSTRAINT fk_activity_logs_project_tenant
        FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_activity_logs_scope CHECK (client_id IS NOT NULL OR project_id IS NOT NULL)
);
CREATE INDEX ix_activity_logs_project_time
    ON client_onboarding.activity_logs (organization_id, project_id, occurred_at DESC, id DESC)
    WHERE project_id IS NOT NULL;
CREATE INDEX ix_activity_logs_client_time
    ON client_onboarding.activity_logs (organization_id, client_id, occurred_at DESC, id DESC)
    WHERE client_id IS NOT NULL;
