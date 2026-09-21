# Client Onboarding & Relationship Management Platform

Production-oriented modular monolith for a multi-tenant B2B SaaS onboarding platform. The implemented
boundary is Phase 3: identity and tenancy, client/project core, and the versioned workflow engine. Client
invitations and portal behavior are intentionally absent because they belong to Phase 4.

## Phase status

- Implemented: Phase 0 foundation; Phase 1 identity/auth/RBAC/tenancy; Phase 2 clients, contacts, services,
  and projects; Phase 3 workflow templates, versions, instances, steps, dependencies, conditions, and readiness.
- Not started: Phase 4 client invitation and portal, and Phases 5–13.
- Release evidence is recorded in `docs/phase-reports/phase-3.md`. The reviewed Phase 3 branch passed backend,
  frontend, real-Chromium/PostgreSQL, and production-container gates in
  [GitHub Actions run 35592880143](https://github.com/JetyChodipilli/Client-Onboarding/actions/runs/35592880143).

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

Or run the complete Phase 3 gate in an environment with PostgreSQL, Chromium, and Docker:

```bash
./scripts/verify-phase-3.sh
```

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

## API surface through Phase 3

- Identity: login, MFA, session refresh/logout, recovery, verification, internal employee invitations,
  organizations, members, roles, permissions, and audit reads.
- Client core: clients, contacts, archive behavior, bounded search, and pagination.
- Service catalog: tenant-owned services and archive behavior.
- Projects: client/service ownership, members, lifecycle transitions, activity history, and archive behavior.
- Workflow: templates, draft versions, ordered steps, conditions, dependencies, publication, and archival.
- Onboarding foundation: idempotent instance creation from a published version, instance reads, step transitions,
  dependency unlocking, progress, and readiness. Client invitations and portal APIs are not present.

The static contract is in `docs/openapi/openapi.yaml`; the running application exposes `/v3/api-docs`.

## Phase boundary

Do not add Phase 4 client invitations or portal behavior, or any Phase 5+ feature, until explicitly requested.
