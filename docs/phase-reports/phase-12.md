# Phase 12 — Dashboards, Reports & Analytics

## Scope

Phase 12 adds tenant-scoped operational, funnel, financial, contract and duration reporting without creating a second source of truth. Reporting queries read PostgreSQL domain tables and do not mutate sibling-module persistence.

## Implemented source

- `GET /api/v1/reports/snapshot` guarded by `REPORT_READ`.
- `GET /api/v1/reports/onboardings` with bounded pagination and optional validated onboarding-status filter.
- Operational metrics: active onboardings, client action, internal review, overdue steps, payment/contract/asset/access backlog, ready-for-approval and completion rate.
- Funnel completion grouped by snapshotted workflow step type.
- Invoice/payment collection and payment-duration metrics with currency-separated monetary totals; currencies are never summed into a misleading cross-currency amount.
- Contract completion and signature-duration metrics.
- Average/median onboarding duration plus waiting-time estimates from append-oriented step transition activity.
- Internal `/app/reports` responsive dashboard with portfolio table and direct links to projects.
- V13 reporting indexes only; no duplicated reporting aggregate tables.
- Playwright source for report rendering and mobile overflow regression.

## Release-audit corrections

- Aligned portfolio progress SQL with the canonical onboarding progress policy: `SKIPPED` counts as satisfied and informational `EXTERNAL_LINK` steps are excluded alongside welcome/instruction/video guide steps.
- Excluded completed/cancelled/expired/paused onboardings from overdue, missing-asset and missing-access operational backlog counts so historical records cannot inflate current work.
- Suppressed per-onboarding overdue counts for terminal/paused onboarding rows.

## Security

Every reporting SQL path includes `organization_id`. The controller requires `REPORT_READ`; the browser permission gate is UX only. User-provided status is parameterized and validated. Reporting remains read-only.

## Verification status

Static source checks are available, but this environment does not contain Maven, Docker/PostgreSQL or installed frontend dependencies. The strict phase gate therefore remains unverified until the full build, Testcontainers/Flyway, Next build and Playwright suites execute successfully.
