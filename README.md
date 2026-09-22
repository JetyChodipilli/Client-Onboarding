# Client Onboarding & Relationship Management Platform

Production-oriented modular monolith for a multi-tenant B2B SaaS onboarding platform. The implemented
boundary is Phase 4: identity and tenancy, client/project core, the versioned workflow engine, secure client
invitations, and a project-scoped client portal.

## Phase status

- Implemented: Phase 0 foundation; Phase 1 identity/auth/RBAC/tenancy; Phase 2 clients, contacts, services,
  and projects; Phase 3 workflow templates, versions, instances, steps, dependencies, conditions, and readiness;
  Phase 4 client invitation, activation, client authorization, and portal UX.
- Not started: Phases 5–13.
- Release evidence is recorded in `docs/phase-reports/phase-4.md`.

## Repository map

```text
.
├── backend/                 Spring Boot modular monolith
├── frontend/                Next.js application and design system
├── design-system/           UI/UX source of truth
├── docs/
│   ├── adr/                 Architecture decision records
│   ├── architecture/        Boundaries, conventions and threat model
│   ├── openapi/             Static API contract
│   └── phase-reports/       Phase completion evidence
├── infrastructure/          Container foundations
├── scripts/                 Reproducible phase gates
├── .github/workflows/       CI pipeline
├── docker-compose.yml       PostgreSQL and application stack
└── AGENTS.md                Implementation rules
```

## Technology baseline

- Java 17 LTS, Spring Boot 4.1, Maven
- PostgreSQL 17 and Flyway
- Next.js 16, React 19, TypeScript, Tailwind CSS 4
- JUnit, Spring Boot Test, ArchUnit, Testcontainers
- Vitest, React Testing Library, Playwright
- Docker Compose and GitHub Actions

## Local development

1. Copy `.env.example` to `.env` and generate `MFA_ENCRYPTION_KEY` with `openssl rand -base64 32`.
2. For a one-time first-tenant bootstrap, enable `APP_BOOTSTRAP_ENABLED` and provide the documented
   `APP_BOOTSTRAP_*` values. Disable bootstrap after it succeeds.
3. Run `docker compose up --build`.
4. Open the frontend at `http://localhost:3000`, readiness at `http://localhost:8080/health/ready`, and
   Swagger UI at `http://localhost:8080/swagger-ui.html`.

Production must use TLS, `SESSION_COOKIE_SECURE=true`, an exact CORS allowlist, an AES-256 MFA key,
authenticated STARTTLS SMTP as required by the provider, and a trusted ingress that strips untrusted
forwarding headers. Secrets must come from an approved secret manager.

## Verification

Run individual gates:

```bash
cd backend
./mvnw verify

cd ../frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

Run the local build, migration, browser, and image-build checks with Chromium and Docker available:

```bash
./scripts/verify-phase-4.sh
```

The authoritative full-stack gate is the GitHub Actions CI workflow. Its browser job uses a fresh PostgreSQL
database and bootstrap account, performs MFA enrollment, sends an invitation through the real SMTP adapter to
a loopback test inbox, and verifies client activation, login, progress, access denial, logout, and link reuse.
The container job also starts the complete Compose stack and checks readiness and the client sign-in page.
For the live browser flow locally, use the CI environment settings with a fresh test database, leave port 1025
free for the test inbox, and run `LIVE_BACKEND=1 npm run test:e2e`. Ordinary browser runs skip that live-only case.

The backend starts Flyway automatically and validates migrations against PostgreSQL. Hibernate schema
generation is disabled.

## Architecture invariants

- Every tenant-owned table and repository path is scoped by authenticated `organization_id`.
- Backend permission checks are authoritative; frontend checks are usability aids.
- Role names never authorize behavior. Privileged permissions require an MFA-assured session.
- Project, onboarding, step, invoice, payment, contract, form, asset, access, and task states are separate.
- A published workflow version is immutable. Starting onboarding creates an immutable JSON snapshot and
  materialized step instances.
- Conditions use a validated, non-executable DSL; dependencies are same-version/same-instance and acyclic.
- Readiness is true only when every applicable blocking step is `COMPLETED`.
- Business updates use optimistic locking, database constraints, bounded queries, and append-oriented audit.
- Redis and Kafka remain absent until a measured requirement justifies them.

See the [architecture overview](docs/architecture/README.md), [module map](docs/architecture/module-map.md),
[state boundaries](docs/architecture/state-machine-boundaries.md), and
[workflow ADR](docs/adr/0008-versioned-workflow-snapshots.md).

## API surface through Phase 4

- Identity: login, MFA, session refresh/logout, recovery, verification, internal employee invitations,
  organizations, members, roles, permissions, and audit reads.
- Client core: clients, contacts, archive behavior, bounded search, and pagination.
- Service catalog: tenant-owned services and archive behavior.
- Projects: client/service ownership, members, lifecycle transitions, activity history, and archive behavior.
- Workflow: templates, draft versions, ordered steps, conditions, dependencies, publication, and archival.
- Onboarding foundation: idempotent instance creation from a published version, instance reads, step transitions,
  dependency unlocking, progress, and readiness.
- Client portal: invitation create/list/resend/revoke/inspect/accept, separate client sign-in and recovery,
  explicit project grants, project portfolio/dashboard, and constrained informational-step transitions.

The static contract is in `docs/openapi/openapi.yaml`; the running application exposes `/v3/api-docs`.

## Phase boundary

Do not add Phase 5 forms/questionnaires or any later-phase feature until explicitly requested.
