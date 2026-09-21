# Phase 3 completion report

PHASE COMPLETED:
Phase 3 implementation is complete. The Definition-of-Done release gate remains open until the published branch
passes backend, clean PostgreSQL migration, Playwright, and container CI. Phase 4 was not started.

IMPLEMENTED:
- Tenant-owned workflow templates with service scope, archive behavior, and permission-based access.
- Monotonic template versions, mutable drafts, immutable publication, and published-version copy to new drafts.
- Ordered typed steps with required/optional, blocking/non-blocking, visibility, review, assignment, due-time,
  reminder reference, skip, reopen, configuration, condition, and dependency rules.
- Acyclic `NONE`/`ALL`/`ANY` graphs with duplicate, dangling, self-edge, and readiness-deadlock validation.
- Safe field/operator/value condition DSL with no tenant-authored expression execution.
- Idempotent onboarding start, immutable JSON snapshots, step materialization, progress, dependency unlocking,
  review transitions, and readiness calculation.
- Explicit onboarding action ownership, blocker, deadline, waiting-party, and help UI without Phase 4 portal scope.

DATABASE MIGRATIONS:
- Added `V5__workflow_engine.sql`.
- Adds workflow templates/versions/steps/dependencies, onboarding instances/step instances/runtime dependencies,
  idempotency records, permissions, tenant constraints, optimistic versions, and indexes.
- V1–V4 were previously exercised against PostgreSQL 17.10. V5 could not be executed locally because Maven
  Central is unreachable and neither Maven nor Docker is installed; CI is the required final proof.

API ENDPOINTS:
- Workflow template list/create/read/archive.
- Template version list/create/read, optimistic step replacement, and publication.
- Idempotent project onboarding start and project/instance reads.
- Optimistic onboarding-step transitions with separate progression and review authorization.
- Static OpenAPI now documents 47 paths through Phase 3; runtime `/v3/api-docs` remains authoritative.

UI SCREENS:
- Workflow template portfolio and creation.
- Draft/published version selector and ordered workflow editor.
- Dependency, condition, assignment, deadline, review, visibility, skip, and reopen controls.
- Project onboarding start and runtime status/progress/readiness/action ownership.
- Loading, empty, denied, validation, conflict, API-error, read-only, locked, and inapplicable states.

TESTS ADDED:
- Workflow condition, graph, readiness, step lifecycle, and onboarding lifecycle policies.
- Phase 2/3 integration scenarios for authorization, tenant isolation, publication immutability, snapshot isolation,
  conditional steps, ALL/ANY dependencies, reviewer separation, idempotent replay, and readiness.
- 36 backend test methods are committed; the dependency-free domain model compiles to 43 classes locally.
- Frontend ESLint and strict TypeScript pass; eight Vitest tests pass across four files.
- Next.js production build and production-server security-header/content probe pass.

PLAYWRIGHT SCENARIOS:
- 52 scenarios are discovered across desktop, tablet, mobile portrait, and mobile landscape.
- Four Phase 3 desktop scenarios were attempted; all stopped before page execution because the Playwright
  Chromium executable is unavailable. The application server itself starts successfully.
- CI installs Chromium and runs the full matrix, including a live backend/MFA path.

SECURITY VALIDATION:
- Workflow/onboarding repositories scope every resource by `organization_id`; cross-tenant reads return 404.
- `WORKFLOW_MANAGE` and `ONBOARDING_REVIEW` require MFA-assured sessions.
- Normal progression requires `ONBOARDING_START`; review actions require `ONBOARDING_REVIEW`.
- Published versions and snapshots are immutable; optimistic locks and row locks close edit/archive/idempotency races.
- Conditions are allowlisted data comparisons, never code. DTO allowlists, CSRF, exact CORS, CSP, parameterized
  JDBC, bounded queries, and generic constraint failures remain enabled.

DESIGN PATTERNS USED:
- Modular monolith, domain policy, repository port/adapter, application transaction, immutable snapshot,
  state machine, safe DSL, optimistic locking, transactional tenant lock, and permission-based RBAC.
- UI follows feature ownership, semantic tokens, keyboard alternatives, responsive reflow, and reduced motion.

KNOWN LIMITATIONS:
- This workspace cannot download Maven, launch Chromium, or run Docker.
- The local Maven attempt stops before project compilation with `UnknownHostException: repo.maven.apache.org`.
- Phase 3 leaves onboarding lifecycle in `DRAFT`; invitation/client progression is Phase 4 and activation is Phase 11.

PRD ITEMS COMPLETED:
- Workflow templates, versions, steps, dependencies, conditions, blocking/required rules, assignments, due dates,
  skip/reopen rules, onboarding instances, step instances, state validation, snapshot isolation, and readiness.
- Sequential, parallel, ALL, ANY, conditional, optional, blocking, edited-template, authorization, and tenant cases
  are represented in committed automated tests.

PRD ITEMS REMAINING:
- Successful backend, V5 clean PostgreSQL, full Playwright, and container CI execution.
- Phase 4–13 are intentionally not implemented.

BUILD STATUS:
FAIL — frontend and dependency-free Java domain builds pass; the required full Maven build is blocked locally.

TEST STATUS:
FAIL — available tests pass, but mandatory backend/PostgreSQL/Playwright/container execution is incomplete.

READY FOR NEXT PHASE:
NO — do not start Phase 4 until the published branch CI passes.
