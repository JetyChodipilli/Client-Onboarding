# Client Onboarding Platform — Phase 0–13 Release Report

## Scope

This repository contains source implementation through the canonical PRD plan:

1. Phase 0 — Architecture & Foundation
2. Phase 1 — Identity, Authentication, RBAC & Tenancy
3. Phase 2 — Clients, Contacts, Services & Projects
4. Phase 3 — Workflow Engine
5. Phase 4 — Client Invitations & Client Portal
6. Phase 5 — Forms & Questionnaires
7. Phase 6 — Asset Management
8. Phase 7 — Billing, Invoices & Payments
9. Phase 8 — Contracts & E-Signatures
10. Phase 9 — Platform Access Management
11. Phase 10 — Notifications, Tasks & Reminder Engine
12. Phase 11 — Readiness Engine, Review & Project Activation
13. Phase 12 — Dashboards, Reports & Analytics
14. Phase 13 — Security Hardening, E2E Testing & Production Readiness

## Final audit fixes

- Corrected Phase 10 invalid Java text-block syntax in reminder/notification workers.
- Hardened the Phase 11 project `READY` database transition to permit only `ONBOARDING -> READY` backed by completed onboarding.
- Added database regression test source for the forbidden arbitrary `DRAFT -> READY` jump.
- Defined a narrow Billing/Payments financial consistency boundary and updated ArchUnit so only those two financial packages may share their persistence repositories; all unrelated modules remain isolated.
- Removed trailing-whitespace defects that caused `git diff --check` failure.
- Added Phase 12 bounded/validated reporting-filter tests.

## Additional full-bundle audit fixes

- Closed a client task/workflow review bypass and aligned workflow-task lock ordering with the canonical onboarding-first lock order.
- Added controlled completion for generic workflow step types while explicitly denying feature-owned form/file/payment/contract/access/manual-task bypass.
- Added client Profile and credential-safe Integrations screens, grouped internal navigation, mobile client navigation and stronger frontend CSP.
- Added regression-test source for review-required workflow tasks and generic-step feature bypass.
- Added an explicit safe cache/adoption policy instead of introducing stale Redis state without measured need.
- Fixed workflow-task synchronization so a task-level review does not strand non-review MANUAL_TASK steps in `SUBMITTED`, and completed review-required tasks still follow the full workflow review lifecycle.
- Completed the internal generic-step `requiresReview` path (`IN_PROGRESS -> SUBMITTED -> UNDER_REVIEW -> COMPLETED`).
- Fixed `MalwareScanner.ScanResult` static factory naming that conflicted with the Java record accessor and would have blocked compilation.
- Reconciled Phase 12 report progress with the canonical progress policy (`SKIPPED` satisfied; informational external-link steps excluded) and removed terminal/paused onboarding records from current backlog/overdue metrics.
- Strengthened the production startup gate so configured payment/e-signature provider codes must resolve to an actually available adapter, not merely have a non-sandbox-looking name.
- Restored missing high-impact operational screens and routes for Assets, Billing/Invoices and Contracts, plus client invoice and contract experiences. PAYMENT and CONTRACT portal next actions now resolve to implemented routes.
- Added a Phase 7/8 browser-regression specification covering the previously broken routes.
- Added `V15__query_performance_hardening.sql` for concrete high-frequency query paths instead of speculative broad indexing.
- Added `V16__server_side_search_indexes.sql` and bounded server-side client/project/service search so large catalogs are not truncated to the first frontend page.
- Added pgjdbc/Hibernate batching and prepared-statement tuning with environment-controlled conservative defaults.
- Removed the notification fan-out `exists` + `insert` race and per-recipient preference/email N+1 queries by using database idempotency plus resolver-loaded delivery metadata.

## Phase 12 — Dashboards, Reports & Analytics

- `GET /api/v1/reports/snapshot` and bounded `/api/v1/reports/onboardings` guarded by `REPORT_READ`.
- Operational dashboard, funnel, invoice/payment, contract and duration metrics. Financial monetary totals are grouped by currency rather than incorrectly summed across currencies.
- Every reporting query is tenant-scoped by `organization_id`; reporting owns no business mutation tables.
- Responsive `/app/reports` internal dashboard with Playwright source.
- V13 reporting query indexes only; PostgreSQL remains the source of truth.

## Phase 13 — Production hardening

- Production startup gate rejects insecure local defaults, public docs, bootstrap mode, insecure origins/cookies, disabled storage/scanning and sandbox/disabled financial/legal providers.
- API and frontend security headers.
- Read-only/no-new-privileges/drop-capabilities Docker hardening.
- CodeQL and dependency-review workflows.
- Security policy, release checklist and operational runbook.
- V14 transition/observability hardening.
- V15 concrete query-path performance indexes; no security/workflow/financial state moved into a cache.
- V16 tenant-prefixed expression indexes for escaped prefix search.
- Phase 13 Playwright security-header, anonymous-route and mobile-overflow source.

## Static evidence collected in this runtime

- Flyway filenames are contiguous V1–V16.
- `frontend/package.json` parses as JSON.
- `backend/pom.xml` parses as XML.
- Compose, Spring YAML and GitHub workflow YAML parse successfully.
- Java main+test sources: 604 files inspected by `javac` parser; no grammar/text-block parse errors after fixes. Dependency resolution is unavailable, so this is not a full Java compile.
- Frontend: 129 non-declaration TS/TSX files parsed with the available TypeScript 5.8.3 parser; zero syntax diagnostics and zero heuristic unused imports.
- `git diff --check` passes.
- No private-key/AWS/OpenAI-like live secret signatures found by the static signature scan.
- No arbitrary `eval`, Java script engine or SpEL execution path found.

## Mandatory executable verification blockers

The execution environment does not provide Maven, Docker or PostgreSQL tooling, `frontend/node_modules` is absent, and npm registry DNS returns `EAI_AGAIN`. Therefore this report does **not** claim the following were executed successfully:

- `mvn verify` / full Java compilation
- Spring Boot startup
- clean PostgreSQL Flyway V1→V16 migration
- Testcontainers integration/security tests
- Docker Compose startup
- frontend lint/typecheck/unit/production build with the declared dependency tree
- Playwright browser execution

A production release must keep the release-readiness checklist open until those commands pass in CI or a suitable build environment.

## Production provider gate

Only disabled/signed-sandbox payment and e-signature adapters are present in this repository. The production profile intentionally rejects them. A real approved provider adapter and production secrets must be configured before production startup; the validator must not be weakened simply to deploy.

## Final gate

- **Source implementation:** Phase 0–13 bundled.
- **Static source validation:** PASS for checks listed above.
- **Mandatory executable build:** NOT VERIFIED / FAIL gate.
- **Mandatory executable test suite:** NOT VERIFIED / FAIL gate.
- **Production ready:** NO until the release checklist is completed with real infrastructure/provider evidence.
