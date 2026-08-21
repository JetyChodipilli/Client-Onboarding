-- Phase 10: tasks, in-app/email notifications, delivery logs, preferences,
-- reminder policies, scheduled reminders, and outbox-to-notification receipts.

INSERT INTO client_onboarding.permissions (id, code, category, description) VALUES
('00000000-0000-0000-0000-000000000037', 'TASK_READ', 'TASK', 'Read tenant-scoped tasks and assignments.'),
('00000000-0000-0000-0000-000000000038', 'TASK_MANAGE', 'TASK', 'Create, assign, update, and cancel tenant-scoped tasks.'),
('00000000-0000-0000-0000-000000000039', 'NOTIFICATION_MANAGE', 'NOTIFICATION', 'Manage organization notification templates and inspect deliveries.'),
('00000000-0000-0000-0000-000000000040', 'REMINDER_MANAGE', 'REMINDER', 'Create, update, archive, and inspect reminder policies and schedules.');

INSERT INTO client_onboarding.role_permissions (organization_id, role_id, permission_id, created_by)
SELECT r.organization_id, r.id, p.id, r.created_by
FROM client_onboarding.roles r
CROSS JOIN client_onboarding.permissions p
WHERE r.system_role = TRUE
  AND r.code = 'ORGANIZATION_ADMIN'
  AND r.status = 'ACTIVE'
  AND p.code IN ('TASK_READ','TASK_MANAGE','NOTIFICATION_MANAGE','REMINDER_MANAGE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE client_onboarding.tasks (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    project_id UUID NULL,
    onboarding_id UUID NULL,
    step_id UUID NULL,
    title VARCHAR(220) NOT NULL,
    description VARCHAR(4000) NULL,
    task_type VARCHAR(32) NOT NULL CHECK (task_type IN ('MANUAL','SYSTEM_GENERATED','WORKFLOW_GENERATED')),
    status VARCHAR(24) NOT NULL DEFAULT 'TODO'
        CHECK (status IN ('TODO','IN_PROGRESS','BLOCKED','IN_REVIEW','COMPLETED','CANCELLED')),
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    assigned_user_id UUID NULL REFERENCES client_onboarding.users(id),
    assigned_user_type VARCHAR(16) NULL CHECK (assigned_user_type IS NULL OR assigned_user_type IN ('INTERNAL','CLIENT')),
    assigned_role_id UUID NULL,
    due_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    source_key VARCHAR(180) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL REFERENCES client_onboarding.users(id),
    created_by_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' CHECK (created_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_by_type VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' CHECK (updated_by_type IN ('INTERNAL','CLIENT','SYSTEM')),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_tasks_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_tasks_org_source UNIQUE (organization_id, source_key),
    CONSTRAINT fk_tasks_project FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id),
    CONSTRAINT fk_tasks_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_tasks_step FOREIGN KEY (organization_id, onboarding_id, step_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id),
    CONSTRAINT fk_tasks_assigned_role FOREIGN KEY (organization_id, assigned_role_id)
        REFERENCES client_onboarding.roles(organization_id, id),
    CONSTRAINT ck_tasks_scope CHECK (
        (step_id IS NULL OR onboarding_id IS NOT NULL) AND
        (onboarding_id IS NULL OR project_id IS NOT NULL)
    ),
    CONSTRAINT ck_tasks_assignee CHECK (
        ((assigned_user_id IS NULL AND assigned_user_type IS NULL) OR
         (assigned_user_id IS NOT NULL AND assigned_user_type IS NOT NULL)) AND
        NOT (assigned_user_id IS NOT NULL AND assigned_role_id IS NOT NULL)
    ),
    CONSTRAINT ck_tasks_actor_create CHECK (
        (created_by_type='SYSTEM' AND created_by IS NULL) OR (created_by_type<>'SYSTEM' AND created_by IS NOT NULL)
    ),
    CONSTRAINT ck_tasks_actor_update CHECK (
        (updated_by_type='SYSTEM' AND updated_by IS NULL) OR (updated_by_type<>'SYSTEM' AND updated_by IS NOT NULL)
    ),
    CONSTRAINT ck_tasks_completion CHECK (
        (status='COMPLETED' AND completed_at IS NOT NULL) OR (status<>'COMPLETED' AND completed_at IS NULL)
    )
);
CREATE INDEX ix_tasks_org_status_due ON client_onboarding.tasks (organization_id, status, due_at, id);
CREATE INDEX ix_tasks_org_assignee ON client_onboarding.tasks (organization_id, assigned_user_id, status, due_at, id)
    WHERE assigned_user_id IS NOT NULL;
CREATE INDEX ix_tasks_project ON client_onboarding.tasks (organization_id, project_id, status, due_at, id)
    WHERE project_id IS NOT NULL;
CREATE INDEX ix_tasks_step ON client_onboarding.tasks (organization_id, step_id) WHERE step_id IS NOT NULL;

CREATE OR REPLACE FUNCTION client_onboarding.validate_task_actor_and_assignee()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.created_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task creator is not an active internal organization user'; END IF;
    IF NEW.created_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.created_by AND cu.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task creator is not an active client user'; END IF;
    IF NEW.updated_by_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task updater is not an active internal organization user'; END IF;
    IF NEW.updated_by_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.updated_by AND cu.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task updater is not an active client user'; END IF;
    IF NEW.assigned_user_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.assigned_user_id AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'task assignee is not an active internal organization user'; END IF;
    IF NEW.assigned_user_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        LEFT JOIN client_onboarding.client_user_projects cup
          ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.assigned_user_id AND cu.status='ACTIVE'
          AND (NEW.project_id IS NULL OR (cup.project_id=NEW.project_id AND cup.status='ACTIVE'))
    ) THEN RAISE EXCEPTION 'task assignee is not an active client user for this scope'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_tasks_validate_actor_assignee
BEFORE INSERT OR UPDATE OF organization_id, project_id, created_by, created_by_type, updated_by, updated_by_type, assigned_user_id, assigned_user_type
ON client_onboarding.tasks
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_task_actor_and_assignee();

CREATE TABLE client_onboarding.notification_templates (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(180) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    recipient_scope VARCHAR(32) NOT NULL CHECK (recipient_scope IN ('PROJECT_CLIENTS','PROJECT_MEMBERS','PERMISSION','ACTOR')),
    required_permission VARCHAR(100) NULL REFERENCES client_onboarding.permissions(code),
    subject_template VARCHAR(300) NOT NULL,
    body_template VARCHAR(6000) NOT NULL,
    action_path_template VARCHAR(1000) NULL,
    channels JSONB NOT NULL DEFAULT '["IN_APP"]'::jsonb,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL REFERENCES client_onboarding.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL REFERENCES client_onboarding.users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_notification_templates_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_notification_templates_org_code UNIQUE (organization_id, code),
    CONSTRAINT ux_notification_templates_org_event UNIQUE (organization_id, event_type),
    CONSTRAINT ck_notification_templates_channels CHECK (
        jsonb_typeof(channels)='array' AND jsonb_array_length(channels) BETWEEN 1 AND 2 AND
        channels <@ '["EMAIL","IN_APP"]'::jsonb
    ),
    CONSTRAINT ck_notification_templates_permission CHECK (
        recipient_scope<>'PERMISSION' OR required_permission IS NOT NULL
    )
);
CREATE INDEX ix_notification_templates_org_status ON client_onboarding.notification_templates (organization_id, status, event_type);

CREATE OR REPLACE FUNCTION client_onboarding.validate_notification_template_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.created_by IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.created_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification template creator is not an active internal organization user'; END IF;
    IF NEW.updated_by IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.updated_by AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification template updater is not an active internal organization user'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_notification_templates_validate_actor
BEFORE INSERT OR UPDATE OF organization_id, created_by, updated_by
ON client_onboarding.notification_templates
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_notification_template_actor();

CREATE TABLE client_onboarding.notifications (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    recipient_user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    recipient_type VARCHAR(16) NOT NULL CHECK (recipient_type IN ('INTERNAL','CLIENT')),
    template_id UUID NULL,
    event_type VARCHAR(100) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id UUID NOT NULL,
    project_id UUID NULL,
    title VARCHAR(300) NOT NULL,
    body VARCHAR(6000) NOT NULL,
    action_url VARCHAR(1000) NULL,
    dedupe_key VARCHAR(220) NOT NULL,
    read_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_notifications_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_notifications_recipient_dedupe UNIQUE (organization_id, recipient_user_id, dedupe_key),
    CONSTRAINT fk_notifications_template FOREIGN KEY (organization_id, template_id)
        REFERENCES client_onboarding.notification_templates(organization_id, id),
    CONSTRAINT fk_notifications_project FOREIGN KEY (organization_id, project_id)
        REFERENCES client_onboarding.projects(organization_id, id)
);
CREATE INDEX ix_notifications_recipient_unread ON client_onboarding.notifications (organization_id, recipient_user_id, read_at, created_at DESC, id DESC);
CREATE INDEX ix_notifications_project ON client_onboarding.notifications (organization_id, project_id, created_at DESC, id DESC) WHERE project_id IS NOT NULL;

CREATE OR REPLACE FUNCTION client_onboarding.validate_notification_recipient()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.recipient_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.recipient_user_id AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification recipient is not an active internal organization user'; END IF;
    IF NEW.recipient_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        LEFT JOIN client_onboarding.client_user_projects cup
          ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.recipient_user_id AND cu.status='ACTIVE'
          AND (NEW.project_id IS NULL OR (cup.project_id=NEW.project_id AND cup.status='ACTIVE'))
    ) THEN RAISE EXCEPTION 'notification recipient is not an active client user for this scope'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_notifications_validate_recipient
BEFORE INSERT OR UPDATE OF organization_id, recipient_user_id, recipient_type, project_id
ON client_onboarding.notifications
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_notification_recipient();

CREATE TABLE client_onboarding.notification_deliveries (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    notification_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('EMAIL','IN_APP')),
    recipient_address VARCHAR(320) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','SENT','DELIVERED','FAILED','DEAD','SUPPRESSED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    last_error VARCHAR(1000) NULL,
    provider_message_id VARCHAR(240) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_notification_deliveries_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_notification_deliveries_channel UNIQUE (notification_id, channel),
    CONSTRAINT fk_notification_deliveries_notification FOREIGN KEY (organization_id, notification_id)
        REFERENCES client_onboarding.notifications(organization_id, id)
        ON DELETE CASCADE
);
CREATE INDEX ix_notification_deliveries_due ON client_onboarding.notification_deliveries (status, next_attempt_at, created_at, id)
    WHERE status IN ('QUEUED','FAILED');
CREATE INDEX ix_notification_deliveries_notification ON client_onboarding.notification_deliveries (organization_id, notification_id);

CREATE TABLE client_onboarding.notification_preferences (
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    user_id UUID NOT NULL REFERENCES client_onboarding.users(id),
    recipient_type VARCHAR(16) NOT NULL CHECK (recipient_type IN ('INTERNAL','CLIENT')),
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organization_id, user_id, recipient_type)
);

CREATE OR REPLACE FUNCTION client_onboarding.validate_notification_preference_user()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.recipient_type='INTERNAL' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.organization_users ou
        WHERE ou.organization_id=NEW.organization_id AND ou.user_id=NEW.user_id AND ou.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification preference user is not an active internal organization user'; END IF;
    IF NEW.recipient_type='CLIENT' AND NOT EXISTS (
        SELECT 1 FROM client_onboarding.client_users cu
        WHERE cu.organization_id=NEW.organization_id AND cu.user_id=NEW.user_id AND cu.status='ACTIVE'
    ) THEN RAISE EXCEPTION 'notification preference user is not an active client user'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_notification_preferences_validate_user
BEFORE INSERT OR UPDATE OF organization_id, user_id, recipient_type
ON client_onboarding.notification_preferences
FOR EACH ROW EXECUTE FUNCTION client_onboarding.validate_notification_preference_user();

CREATE TABLE client_onboarding.reminder_policies (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NULL,
    initial_delay_minutes INTEGER NOT NULL CHECK (initial_delay_minutes BETWEEN 0 AND 525600),
    repeat_interval_minutes INTEGER NOT NULL CHECK (repeat_interval_minutes BETWEEN 15 AND 525600),
    maximum_reminders INTEGER NOT NULL CHECK (maximum_reminders BETWEEN 1 AND 50),
    business_hours_only BOOLEAN NOT NULL DEFAULT FALSE,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    channels JSONB NOT NULL DEFAULT '["IN_APP","EMAIL"]'::jsonb,
    stop_when_completed BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_reminder_policies_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_reminder_policies_org_name UNIQUE (organization_id, name),
    CONSTRAINT fk_reminder_policies_creator FOREIGN KEY (organization_id, created_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT fk_reminder_policies_updater FOREIGN KEY (organization_id, updated_by)
        REFERENCES client_onboarding.organization_users(organization_id, user_id),
    CONSTRAINT ck_reminder_policies_channels CHECK (
        jsonb_typeof(channels)='array' AND jsonb_array_length(channels) BETWEEN 1 AND 2 AND
        channels <@ '["EMAIL","IN_APP"]'::jsonb
    )
);
CREATE INDEX ix_reminder_policies_org_status ON client_onboarding.reminder_policies (organization_id, status, updated_at DESC, id DESC);

CREATE TABLE client_onboarding.scheduled_reminders (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    reminder_policy_id UUID NOT NULL,
    project_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    step_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUPPRESSED','COMPLETED')),
    next_run_at TIMESTAMPTZ NOT NULL,
    sent_count INTEGER NOT NULL DEFAULT 0 CHECK (sent_count >= 0),
    last_sent_at TIMESTAMPTZ NULL,
    lease_until TIMESTAMPTZ NULL,
    suppression_reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_scheduled_reminders_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_scheduled_reminders_step_policy UNIQUE (organization_id, step_id, reminder_policy_id),
    CONSTRAINT fk_scheduled_reminders_policy FOREIGN KEY (organization_id, reminder_policy_id)
        REFERENCES client_onboarding.reminder_policies(organization_id, id),
    CONSTRAINT fk_scheduled_reminders_onboarding_project FOREIGN KEY (organization_id, onboarding_id, project_id)
        REFERENCES client_onboarding.onboarding_instances(organization_id, id, project_id),
    CONSTRAINT fk_scheduled_reminders_step FOREIGN KEY (organization_id, onboarding_id, step_id)
        REFERENCES client_onboarding.onboarding_step_instances(organization_id, onboarding_id, id)
);
CREATE INDEX ix_scheduled_reminders_due ON client_onboarding.scheduled_reminders (status, next_run_at, id)
    WHERE status='ACTIVE';
CREATE INDEX ix_scheduled_reminders_onboarding ON client_onboarding.scheduled_reminders (organization_id, onboarding_id, status, next_run_at);

-- Notification routing receipts are tenant-owned. Add a composite key so a receipt cannot pair
-- one organization with another organization's outbox event even through direct SQL.
ALTER TABLE client_onboarding.outbox_events
    ADD CONSTRAINT ux_outbox_events_org_id UNIQUE (organization_id, id);

CREATE TABLE client_onboarding.notification_event_receipts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES client_onboarding.organizations(id),
    outbox_event_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING','PROCESSED','SKIPPED','FAILED','DEAD')),
    notifications_created INTEGER NOT NULL DEFAULT 0 CHECK (notifications_created >= 0),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ NULL,
    last_error VARCHAR(1000) NULL,
    CONSTRAINT ux_notification_event_receipts_event UNIQUE (outbox_event_id),
    CONSTRAINT ux_notification_event_receipts_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_notification_event_receipts_outbox FOREIGN KEY (organization_id, outbox_event_id)
        REFERENCES client_onboarding.outbox_events(organization_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_notification_event_receipts_org_time ON client_onboarding.notification_event_receipts (organization_id, processed_at DESC, id DESC);

-- Existing workflow definitions may now reference reminder policies. The application validates tenant ownership
-- and ACTIVE state before a workflow version is published; this FK protects snapshot references at the DB layer.
ALTER TABLE client_onboarding.onboarding_template_steps
    ADD CONSTRAINT fk_onboarding_template_steps_reminder_policy
    FOREIGN KEY (organization_id, reminder_policy_id)
    REFERENCES client_onboarding.reminder_policies(organization_id, id);

ALTER TABLE client_onboarding.onboarding_step_instances
    ADD CONSTRAINT fk_onboarding_step_instances_reminder_policy
    FOREIGN KEY (organization_id, reminder_policy_id)
    REFERENCES client_onboarding.reminder_policies(organization_id, id);
