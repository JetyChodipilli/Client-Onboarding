# Phase 2: Clients, contacts, services, and projects

## Ownership

`client` owns client companies and contacts. `servicecatalog` owns tenant-defined service offerings. `project`
owns client/service relationships, internal project membership, its lifecycle, and its activity timeline.

Every query includes `organization_id`; tenant relationship foreign keys include it as part of the referenced
key. Client, contact, service, project, and membership archives preserve history instead of deleting it.

## Project rules

- One client may own many projects; each project has one client and one service.
- Client/service relationships may change only while the project is `DRAFT`.
- The selected service must be active.
- Lifecycle transitions are centralized in `ProjectStatePolicy`; onboarding moves `DRAFT` to `ONBOARDING`
  through an explicit project application port.
- Optimistic versions prevent lost updates, and lifecycle/activity changes share a transaction.
- At most one non-archived primary contact exists per client, enforced by PostgreSQL.

Client-user portal linkage remains a foundation field only. Client invitations and portal authorization are
Phase 4 and are not exposed here.
