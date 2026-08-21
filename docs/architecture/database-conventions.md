# Database Conventions

PostgreSQL is the system of record. Application schema: `client_onboarding`.

## Core rules

- UUID primary keys unless explicitly documented otherwise.
- `TIMESTAMPTZ`, UTC storage.
- Tenant-owned records carry `organization_id`.
- Mutable aggregates carry audit timestamps/actors and `version` when concurrent updates matter.
- Request-driven tenant lookup by global ID alone is prohibited.
- Critical resource relationships use tenant-composite foreign keys/validation triggers in addition to application authorization.
- Historical security/financial/legal/review evidence is append-oriented or immutable as appropriate.
- Hibernate runs with `ddl-auto=validate`; Flyway is the migration authority.
- Never rewrite an applied migration.

## Migration chain

1. `V1__foundation_schema.sql`
2. `V2__identity_rbac_and_tenancy.sql`
3. `V3__clients_services_projects.sql`
4. `V4__workflow_engine_and_phase2_hardening.sql`
5. `V5__client_invitations_portal_and_client_auth.sql`
6. `V6__forms_questionnaires_and_portal_step_hardening.sql`
7. `V7__asset_management_secure_storage.sql`
8. `V8__billing_invoices_payments.sql`
9. `V9__contracts_esignatures.sql`
10. `V10__platform_access_management.sql`
11. `V11__notifications_tasks_reminders.sql`
12. `V12__readiness_review_activation.sql`
13. `V13__reporting_query_indexes.sql`
14. `V14__production_security_hardening.sql`
15. `V15__query_performance_hardening.sql`
16. `V16__server_side_search_indexes.sql`

## Phase 10/11 safety rails

- notification event receipts bind to the same tenant as their outbox event;
- delivery/task/reminder worker queries use bounded claim leases and deterministic status indexes;
- final review records are append-only;
- review evidence must reference an active internal reviewer, current onboarding version and a lifecycle-consistent state;
- a project cannot be persisted as READY without completed onboarding evidence;
- a project can enter ACTIVE only from READY (or resume an already-active hold).

## Indexing

Index tenant + actual query dimensions, particularly `organization_id`, `client_id`, `project_id`, `onboarding_id`, `status`, `created_at`, `due_at`, worker `available_at/next_run_at`, provider/external IDs and review/event ordering keys. Global worker indexes are allowed only where the worker intentionally scans across tenants; request-driven reads stay tenant-prefixed. `V16` uses tenant-prefixed `lower(...) text_pattern_ops` expression indexes for bounded prefix search without requiring the `pg_trgm` extension. Search inputs escape `%`, `_` and `\` before appending the server-controlled `%` suffix.

Validate new indexes with representative `EXPLAIN (ANALYZE, BUFFERS)` before adding more because indexes increase write amplification and storage.
