# System Design, Cache & Query Performance Hardening — 2026-08-21

## Scope

This pass applies the senior-review fixes to the supplied project bundle and then hardens concrete database/query paths without changing the PRD business model. PostgreSQL remains authoritative for tenancy, RBAC, workflow, readiness, billing, contracts, files and audit state.

## Applied system-design fixes

- Preserved modular-monolith boundaries, ACID application-service transactions, optimistic locking, targeted pessimistic locking and the transactional outbox.
- Kept feature-owned workflow steps behind their own domain commands; the narrow generic-step command cannot complete FORM, FILE_UPLOAD, PAYMENT, CONTRACT, PLATFORM_ACCESS or MANUAL_TASK requirements.
- Preserved tenant-scoped authorization and server-side state-machine validation.
- Preserved production fail-closed provider/storage/security gates rather than replacing them with fake adapters.

## Query-performance changes

### Flyway V15

`V15__query_performance_hardening.sql` adds indexes for query paths that already exist in source:

- notification newest-first feed;
- notification-template name ordering;
- notification-delivery administration ordering;
- scheduled-reminder administration ordering;
- tenant-wide onboarding report started-time ordering when no status filter is selected;
- tenant task all/mine/project due-date lists;
- cross-tenant overdue-invoice worker scan;
- outbox notification routing order;
- notification-routing retry/stale-claim recovery.

Indexes were not added for hypothetical fields or future features. Additional indexes should require staging/production `EXPLAIN (ANALYZE, BUFFERS)` evidence.

### JDBC/Hibernate

`application.yml` now enables conservative, environment-tunable performance settings:

- pgjdbc prepared-statement cache;
- `reWriteBatchedInserts`;
- Hibernate `default_batch_fetch_size`;
- Hibernate JDBC batch size;
- ordered inserts/updates;
- IN-clause parameter padding;
- fail-fast protection for pagination over collection fetch joins.

The settings reduce parse/round-trip cost without weakening transaction boundaries.

### Notification fan-out N+1 removal

The recipient resolver now fetches email plus notification preferences in the same recipient query. Outbox and reminder workers reuse those values instead of querying user email and preferences per recipient.

Notification creation now uses the database unique key with `INSERT ... ON CONFLICT DO NOTHING` as its idempotency boundary. This removes the previous application-side `exists` + `insert` race and one query per attempted notification.

Notification deliveries are inserted with a JDBC batch inside the same transaction as notification creation.

## Cache decision

No Redis/shared cache was added for mutable security or workflow state. This is intentional and follows the PRD rule to use Redis only when justified. Stale authorization, invitation, payment, contract, quarantine, onboarding-step or readiness state is not acceptable.

Future cache candidates remain low-volatility tenant-scoped/versioned reference projections only. Every future key must include `organization_id`, define a bounded TTL, define committed-event invalidation, and have tenant-isolation tests.

## Static verification performed in this environment

- YAML/JSON/XML parse: PASS.
- TypeScript/TSX syntax parse: 129 files, 0 parse diagnostics.
- Frontend `@/` local import resolution: 0 missing imports.
- Flyway versions: V1 through V15 present; 137 explicit indexes; no duplicate index names detected.
- Secret signature heuristic: no private-key/AWS/OpenAI/GitHub token signatures found in source/config excluding examples/docs.
- Risky Java execution heuristic: no `Runtime.exec`, `ProcessBuilder`, script-engine or SpEL execution path found in application source.
- Hard-coded ADMIN-role comparison heuristic: no hits.
- Java source was parsed by `javac`; failures are dependency-resolution errors because Maven dependencies are not available in this sandbox, not Java grammar diagnostics.
- Offline npm lockfile generation was attempted and blocked because `@playwright/test` is not present in the local npm cache.

## Mandatory executable verification still open

This sandbox has Java and Node/TypeScript but does not have Maven, Docker/PostgreSQL, project `node_modules`, or network package resolution. Therefore production release still requires CI/a capable workstation to run:

1. `mvn verify` including Testcontainers tenant/security/concurrency tests.
2. Clean PostgreSQL Flyway V1 -> V15 plus Hibernate schema validation.
3. Spring Boot production-profile startup and log inspection.
4. `npm ci`, dependency audit, lint, semantic typecheck, Vitest and `next build`.
5. Playwright desktop/tablet/mobile regression suite.
6. Provider sandbox/real-adapter webhook tests.
7. `EXPLAIN (ANALYZE, BUFFERS)` on representative large-tenant task/notification/report/worker queries.

Do not mark production readiness green until those executable gates pass.
