# Client Onboarding & Relationship Management Platform

A multi-tenant B2B onboarding system built as a **Spring Boot modular monolith** with PostgreSQL as the source of truth and a Next.js frontend.

I kept this as one deployable backend on purpose. The hard part here is not service-to-service networking; it is keeping identity, tenancy, projects, workflow definitions, workflow execution, and audit history consistent while the product is still evolving.

The current `main` branch is implemented through **Phase 4**.

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

## What is on `main`

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

Full Phase 4 gate:

```bash
./scripts/verify-phase-4.sh
```

The reviewed Phase 3 branch passed backend, frontend, PostgreSQL / Chromium, and production-container gates in [GitHub Actions run 35592880143](https://github.com/JetyChodipilli/Client-Onboarding/actions/runs/35592880143).

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

Implementation stops at Phase 4. Forms, assets, payments, contracts and later phases remain outside the current scope.

Phase 4 architecture: [client invitation and portal](docs/architecture/phase-4-client-invitation-portal.md).
Cross-phase audit: [Phases 0–4 debug review](docs/phase-reports/phases-0-4-debug-audit.md).

I keep that boundary explicit because I would rather have the README describe the code that exists today than turn roadmap work into marketing copy.

Architecture notes: [docs/architecture/README.md](docs/architecture/README.md)
