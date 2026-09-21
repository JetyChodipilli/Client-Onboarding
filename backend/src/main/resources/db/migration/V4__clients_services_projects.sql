INSERT INTO permissions (id, code, description, created_at) VALUES
    ('00000000-0000-0000-0000-000000000023', 'PROJECT_READ', 'Read projects and their activity', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000024', 'SERVICE_MANAGE', 'Manage the organization service catalog', CURRENT_TIMESTAMP);

CREATE TABLE clients (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    name varchar(160) NOT NULL,
    legal_name varchar(200),
    status varchar(24) NOT NULL CHECK (status IN ('PROSPECT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    website varchar(500),
    email varchar(254),
    phone varchar(50),
    notes varchar(2000),
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, name)
);

CREATE TABLE client_contacts (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    client_id uuid NOT NULL,
    name varchar(160) NOT NULL,
    email varchar(254) NOT NULL,
    phone varchar(50),
    job_title varchar(120),
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, client_id, email),
    FOREIGN KEY (organization_id, client_id) REFERENCES clients(organization_id, id)
);

CREATE TABLE client_primary_contacts (
    organization_id uuid NOT NULL,
    client_id uuid NOT NULL,
    contact_id uuid NOT NULL,
    assigned_at timestamptz NOT NULL,
    assigned_by uuid NOT NULL REFERENCES users(id),
    PRIMARY KEY (organization_id, client_id),
    UNIQUE (organization_id, contact_id),
    FOREIGN KEY (organization_id, client_id) REFERENCES clients(organization_id, id),
    FOREIGN KEY (organization_id, contact_id) REFERENCES client_contacts(organization_id, id)
);

CREATE TABLE client_users (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    client_id uuid NOT NULL,
    contact_id uuid,
    user_id uuid NOT NULL REFERENCES users(id),
    status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, user_id),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, client_id) REFERENCES clients(organization_id, id),
    FOREIGN KEY (organization_id, contact_id) REFERENCES client_contacts(organization_id, id)
);

CREATE TABLE services (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    code varchar(80) NOT NULL,
    name varchar(160) NOT NULL,
    description varchar(1000),
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, code)
);

CREATE TABLE projects (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    client_id uuid NOT NULL,
    service_id uuid NOT NULL,
    name varchar(180) NOT NULL,
    description varchar(2000),
    status varchar(24) NOT NULL CHECK (status IN
        ('DRAFT', 'ONBOARDING', 'READY', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    value_minor bigint CHECK (value_minor IS NULL OR value_minor >= 0),
    currency_code char(3),
    target_start_date date,
    previous_status varchar(24),
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL,
    updated_by uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, client_id, name),
    FOREIGN KEY (organization_id, client_id) REFERENCES clients(organization_id, id),
    FOREIGN KEY (organization_id, service_id) REFERENCES services(organization_id, id)
);

CREATE TABLE project_members (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    project_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    assignment_role varchar(80) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    UNIQUE (organization_id, project_id, membership_id),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, project_id) REFERENCES projects(organization_id, id),
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_users(organization_id, id)
);

CREATE TABLE activity_logs (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    project_id uuid NOT NULL,
    actor_user_id uuid REFERENCES users(id),
    action varchar(100) NOT NULL,
    entity_type varchar(80) NOT NULL,
    entity_id uuid NOT NULL,
    details text,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (organization_id, project_id) REFERENCES projects(organization_id, id)
);

CREATE INDEX idx_clients_org_status_created ON clients (organization_id, status, created_at DESC);
CREATE INDEX idx_client_contacts_client ON client_contacts (organization_id, client_id, archived_at);
CREATE INDEX idx_services_org_status_name ON services (organization_id, status, name);
CREATE INDEX idx_projects_org_status_created ON projects (organization_id, status, created_at DESC);
CREATE INDEX idx_projects_client ON projects (organization_id, client_id, status);
CREATE INDEX idx_project_members_project ON project_members (organization_id, project_id);
CREATE INDEX idx_activity_project_created ON activity_logs (organization_id, project_id, created_at DESC);
