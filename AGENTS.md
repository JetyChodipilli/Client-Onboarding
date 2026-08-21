# AGENTS.md

## Source of truth

Read `docs/Client_Onboarding_PRD_SDLC_Implementation_Ready.docx` before changing product behavior. It remains authoritative unless a later approved change explicitly overrides it.

## Current phase boundary

Source implementation extends through **Phase 13 — Security Hardening, E2E Testing & Production Readiness**. Phase 12 owns dashboards/reports/analytics; Phase 13 owns hardening and release verification. Do not pull post-MVP roadmap features into release hardening.

## Non-negotiable architecture rules

- Spring Boot modular monolith; PostgreSQL is the system of record; Flyway owns schema evolution.
- Every tenant-owned request path validates authenticated identity, tenant, permission and resource relationship.
- Role names are not authorization logic; permissions are.
- Modules call controlled application interfaces, never sibling persistence repositories/tables directly.
- Client, Client Contact and authenticated Client User are distinct concepts.
- Project, onboarding, step, invoice, payment, contract, asset, access, task and delivery lifecycles remain independent.
- Workflow versions and published legal/form/access definitions are immutable as required; onboarding snapshots are reproducible.
- Readiness is derived from applicable blocking step instances; final-review revision evidence adds a separate gate and never rewrites the readiness formula.
- Final approval completes onboarding and moves the project to READY. Activation is a separate `PROJECT_ACTIVATE` command from READY only.
- Payment/contract callbacks are verified and idempotent before business-state changes.
- Files are private until security processing succeeds; normal downloads are short-lived and authorized.
- Third-party passwords/tokens must never be requested for platform access.
- Notification/reminder delivery failure must not corrupt domain state; retries are bounded and inspectable.
- Kafka is not an MVP dependency. Transactional outbox + workers remain the reliable async boundary.
- Never rewrite an applied migration.

## Backend modules

`auth`, `identity`, `organization`, `client`, `servicecatalog`, `project`, `workflow`, `onboarding`, `forms`, `assets`, `access`, `billing`, `payments`, `contracts`, `tasks`, `notifications`, `reminders`, `integrations`, `reporting`, `audit`, `common`.

Prefer `api/`, `application/`, `domain/`, `infrastructure/` inside significant modules. Controllers are thin; transactions/use cases live in application services; invariants/state machines live in domain code.

A request-driven tenant aggregate must never be retrieved by raw global ID alone. Use organization-scoped repository methods and database relationship constraints where the invariant is security- or accounting-critical.

Billing and Payments are two application submodules inside one narrowly documented financial consistency boundary (ADR 0007). Only those two packages may share their persistence repositories for atomic ledger/invoice reconciliation; unrelated modules must use application interfaces.

## Phase 12 reporting ownership

- `reporting` is a tenant-scoped, read-only projection over PostgreSQL source-of-truth tables.
- `REPORT_READ` is mandatory server-side.
- Reporting may read across module tables but never mutate them or introduce an independent lifecycle/source of truth.
- Metrics must be reproducible from persisted domain/audit/activity evidence; do not invent client-side analytics.

## Phase 13 production rules

- The `prod` profile must fail closed on sandbox/local defaults.
- Never weaken `ProductionReadinessValidator` to make a deployment pass.
- Production containers should run without extra Linux capabilities and with read-only root filesystems where supported.
- CodeQL/dependency review supplement, but never replace, executed application/security tests.
- A static parse is not a build; no release PASS without the full Maven/Flyway/Next/Playwright gate.

## Phase 11 ownership

- `OnboardingReadinessPolicy`: mathematical readiness = every applicable blocking step is COMPLETED.
- `FinalReviewRevisionGate`: a review-requested non-blocking revision may keep mathematical readiness true but must prevent automatic re-entry into final review until selected revision work is complete.
- `OnboardingFinalReviewService`: checklist, review evidence, approval and revision orchestration.
- Feature-specific `FinalReviewRevisionHandler` implementations own how a form/asset/access/manual-task requirement is reopened.
- Payment and signed-contract facts are not rewritten through final review.
- `ProjectActivationService`: controlled project READY transition after completed onboarding and separate READY → ACTIVE activation.
- `V12__readiness_review_activation.sql`: append-only review evidence and database guards for READY/ACTIVE.

Do not make the browser calculate authoritative readiness or mutate lifecycle status directly.

## Phase 10 reliability rules

- EMAIL and IN_APP are the MVP notification channels.
- Outbox routing, notification delivery and reminder workers use bounded attempts/leases; terminal failure is inspectable.
- Reminder suppression must win over cadence when the step/onboarding/policy is no longer valid.
- Client-visible reminders follow the party currently responsible for action; submitted/under-review work waits on the internal team.
- Notification preferences must affect what is actually surfaced/delivered, not only metadata.

## Frontend conventions

Backend authorization is authoritative. Frontend permission checks only improve UX.

Every major screen needs loading, empty, permission-denied and recoverable error states. Preserve responsive layouts, keyboard focus, semantic labels and text alternatives to status color/icons.

For client/onboarding UX, make status and the primary next action obvious and distinguish **Your Action** from **Waiting for Our Team**.

For final review, clearly distinguish:
1. blockers complete / readiness evidence;
2. human approval or revision;
3. project READY;
4. separate privileged activation.

## Testing requirements

Relevant phases require:
- unit tests for domain policies/state machines/validation;
- PostgreSQL/Testcontainers integration tests for Flyway + tenant/resource constraints;
- authorization and cross-tenant negative tests;
- concurrency/idempotency tests where applicable;
- Playwright for critical responsive browser journeys and console errors.

A static parse is not a build and a source review is not an executed test.

## Completion gate

Before claiming a phase PASS: compile, run unit/integration/security tests, run Flyway from a clean PostgreSQL database, start the application, inspect logs, run frontend lint/typecheck/unit/build/Playwright, update documentation, self-review, fix discovered defects, and report truthfully.
