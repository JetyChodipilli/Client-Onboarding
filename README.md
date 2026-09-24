# Client Onboarding & Relationship Management Platform

A multi-tenant B2B onboarding system built as a **Spring Boot modular monolith** with PostgreSQL as the source of truth and a Next.js frontend.

I kept this as one deployable backend on purpose. The hard part here is not service-to-service networking; it is keeping identity, tenancy, projects, workflow definitions, workflow execution, and audit history consistent while the product is still evolving.

This branch implements **Phase 5 — Forms & Questionnaires**. The phase report records its verification and delivery status.

## Why a modular monolith

Splitting a young domain into services too early would add network failure modes before the boundaries were stable.

So the backend keeps one transaction boundary while still enforcing module boundaries in code.

```text
Browser
  ↓
Next.js
  ↓
Spring Boot /api/v1
  ↓
Domain modules
  ↓
PostgreSQL
```

Redis and Kafka are intentionally absent from this project at the moment. I would rather add them when a real workload requires them than because the architecture diagram looks better with more boxes.

## What is implemented

### Phase 1 — identity and tenancy

- users, organizations and memberships
- server-side sessions
- RBAC and permission checks
- TOTP MFA and recovery codes
- internal employee invitations
- security audit records

### Phase 2 — client and project core

- clients and contacts
- service catalog
- projects and project members
- lifecycle transitions
- archive behavior
- activity history

### Phase 3 — workflow engine

- workflow templates and numbered versions
- draft / publish lifecycle
- ordered steps
- dependency graphs
- safe conditions
- immutable onboarding snapshots
- materialized step instances
- progress and readiness rules
- idempotent onboarding start

### Phase 4 — client invitation and portal

- secure, expiring invitations with resend, revoke and single-use acceptance
- client authentication and explicit project grants
- client dashboard, progress, next action and prerequisite explanations
- informational step submission, revision and internal review boundaries
- separate client and internal permissions

### Phase 5 — forms and questionnaires

- reusable form templates with immutable published versions
- conditional fields and authoritative submission validation
- saved drafts, review, revision feedback, resubmission and approval
- immutable answer snapshots and review history
- project-scoped client collection and permission-scoped internal review
- atomic workflow/readiness updates, audit and durable form events

Open **Forms** in the internal workspace, create and publish a questionnaire, then select that version
on a FORM step in the workflow builder. Clients open questionnaires from their project portal.
Reviewers open **View questionnaire** from the internal project page.

Existing organizations must assign `FORM_READ`, `FORM_MANAGE`, and/or `FORM_REVIEW` through Settings → Roles.
Manage/review permissions require MFA. Flyway applies `V7__forms_and_questionnaires.sql` without
changing V1–V6. Files/uploads remain Phase 6; Phase 10 adds delivery of the durable form events.

## The workflow decision that matters most

A template can change tomorrow. An onboarding process that already started should not silently change with it.

Published workflow versions are therefore immutable. When onboarding starts, the backend stores both the exact JSON snapshot and normalized step instances in the same transaction.

Dependencies reject dangling edges, duplicates, self-references, and cycles. Conditions use a fixed field / operator / value model instead of executing tenant-authored scripts.

That gives the system more structure up front, but it also means an old onboarding run can still be explained later.

Read the decision record: [ADR 0008 — versioned workflow snapshots](docs/adr/0008-versioned-workflow-snapshots.md).

## Backend stack

| Area | Stack |
|---|---|
| Backend | Java 17, Spring Boot 4.1, Spring Security, Spring Data JPA |
| Data | PostgreSQL 17, Flyway |
| Backend testing | JUnit, Spring Boot Test, ArchUnit, Testcontainers |
| Delivery | Docker Compose, GitHub Actions |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Frontend testing | Vitest, React Testing Library, Playwright |

## Run locally

```bash
cp .env.example .env
```

Generate the MFA encryption key:

```bash
openssl rand -base64 32
```

For the first tenant only, enable the documented `APP_BOOTSTRAP_*` values. Disable bootstrap after it succeeds.

Then run:

```bash
docker compose up --build
```

- Frontend: `http://localhost:3000`
- Readiness: `http://localhost:8080/health/ready`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Internal workspace: `http://localhost:3000/app`
- Client sign-in: `http://localhost:3000/client/login`

Configure the SMTP values in `.env.example` before sending invitations. For an existing organization,
assign `ONBOARDING_INVITE` to the appropriate internal role in role settings; it requires MFA.
Clients receive access only to projects explicitly granted through accepted invitations.

## Verification

Backend:

```bash
cd backend
./mvnw verify
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

Full Phase 5 gate:

```bash
LIVE_BACKEND=1 ./scripts/verify-phase-5.sh
```

The local gate requires a fresh running bootstrap backend configured like the CI browser job,
Chromium, Docker and a free loopback SMTP port 1025. CI provisions these prerequisites and also
verifies clean Compose startup and logs. The script refuses to silently skip the live browser scenario.

The cross-phase audit passed backend, frontend, PostgreSQL / Chromium, and production-container gates in [GitHub Actions run 35849125306](https://github.com/JetyChodipilli/Client-Onboarding/actions/runs/35849125306): 55 backend tests, 12 frontend unit tests and 89 browser scenarios passed. Three repeated bootstrap scenarios are intentionally skipped outside the desktop run. Final merge checks are recorded in [PR #26](https://github.com/JetyChodipilli/Client-Onboarding/pull/26).

## Repository map

```text
backend/              Spring Boot modular monolith
frontend/             Next.js application
design-system/        UI/UX source of truth
docs/adr/             Architecture decisions
docs/architecture/    Boundaries, conventions, threat model
docs/openapi/         Static API contract
docs/phase-reports/   Phase verification evidence
infrastructure/       Container foundations
scripts/              Reproducible verification gates
.github/workflows/    CI pipeline
```

## Current boundary

Implementation stops at Phase 5. Assets, payments, contracts and later phases remain outside the current scope.

Phase 5: [architecture and rollout](docs/architecture/phase-5-forms-questionnaires.md) · [phase report](docs/phase-reports/phase-5.md).

Phase 4 architecture: [client invitation and portal](docs/architecture/phase-4-client-invitation-portal.md).
Cross-phase audit: [Phases 0–4 debug review](docs/phase-reports/phases-0-4-debug-audit.md).

I keep that boundary explicit because I would rather have the README describe the code that exists today than turn roadmap work into marketing copy.

Architecture notes: [docs/architecture/README.md](docs/architecture/README.md)
