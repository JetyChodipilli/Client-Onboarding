# Phase 0 Report — Architecture & Foundation

Date: 2026-08-21

## PHASE COMPLETED

**Phase 0 — Architecture & Foundation: implementation complete; Definition-of-Done verification is blocked in the current execution environment.**

No Phase 1 identity, tenancy, client, project, or onboarding business implementation was added.

## IMPLEMENTED

- Git monorepo foundation with `backend/`, `frontend/`, `docs/`, CI, Docker Compose, environment template, Makefile, README, and AGENTS instructions.
- Java 21 / Spring Boot 4.0 foundation using Spring MVC, Security, JPA, Validation, Actuator, Flyway, PostgreSQL, OpenAPI, Prometheus registry, and Testcontainers.
- Next.js 16 / React 19 / TypeScript / Tailwind / shadcn-style primitive foundation.
- Consistent success/error API envelope and global exception handling.
- Request/correlation IDs, MDC logging context, response tracing headers, and structured-log configuration.
- Liveness/readiness endpoints; readiness validates PostgreSQL connectivity.
- Deny-by-default backend security. Only liveness/readiness are anonymous by default; OpenAPI docs are an explicit local/deployment opt-in.
- CORS allowlist foundation, security headers, and a minimal CSP baseline.
- Design tokens, semantic colors, spacing/radius/elevation guidance, accessible focus treatment, loading/error/not-found states, and a Phase-0-only responsive foundation screen.
- Architecture documentation: module map, conceptual domain model, dependency rules, independent state-machine boundaries, DB/API/security/testing/frontend/design-system conventions, threat model, risk register, and ADRs.
- CI includes backend verification, frontend dependency audit/lint/typecheck/unit/build/E2E, and backend/frontend container builds.
- Dependabot foundation for Maven, npm, Docker images, and GitHub Actions.

## DATABASE MIGRATIONS

- `V1__foundation_schema.sql`
  - creates the `client_onboarding` PostgreSQL schema only;
  - intentionally creates no Phase 1+ business tables.
- Hibernate is configured with `ddl-auto=validate`; Flyway owns schema changes.

## API ENDPOINTS

- `GET /health/live`
- `GET /health/ready`
- OpenAPI generation foundation at `/v3/api-docs` when `PUBLIC_API_DOCS=true`.
- Swagger UI foundation at `/swagger-ui.html` when `PUBLIC_API_DOCS=true`.
- All other application routes are denied in Phase 0.

## UI SCREENS

- Responsive Phase 0 architecture/foundation status page.
- Global loading state.
- Global recoverable error state.
- 404/not-found state.
- No business dashboard, authentication screen, client screen, project screen, or onboarding screen was implemented.

## TESTS ADDED

Backend:

- `CorrelationIdFilterTest`
  - safe external request/correlation IDs are propagated;
  - unsafe IDs are replaced;
  - MDC is cleared after the request.
- `ArchitectureTest`
  - API packages cannot depend directly on persistence implementations;
  - domain packages cannot depend on API packages.
- `FoundationApplicationIT`
  - PostgreSQL 17 Testcontainer clean startup;
  - Flyway migration during application startup;
  - liveness/readiness validation;
  - deny-by-default API security validation.

Frontend:

- React Testing Library/Vitest test for the reusable button primitive.
- Playwright foundation scenario with desktop, tablet, and mobile projects.

## PLAYWRIGHT SCENARIOS

Defined, but not executable in this runtime because npm packages and browser binaries cannot be downloaded:

- foundation page renders;
- Phase 0 scope indicator is present;
- future business-action control is disabled;
- no horizontal overflow at responsive widths;
- no browser console errors;
- failure screenshots and first-retry traces are configured.

## SECURITY VALIDATION

Implemented/structurally reviewed:

- deny-by-default application routes;
- no hard-coded production secrets;
- API docs are not public by default;
- actuator metrics are not anonymous;
- request/correlation header validation prevents unbounded/log-injection-style IDs;
- consistent non-stacktrace API errors;
- CORS origin allowlist foundation;
- browser frame/object/base/form security headers;
- threat model covers tenant escape, authorization, token theft/replay, webhook forgery, payment/contract tampering, unsafe uploads, SSRF, mass assignment, injection/XSS, brute force, and insider misuse;
- tenant-scoped repository/application conventions are documented for Phase 1+.

Security release gate:

- On 2026-08-20, Next.js announced an August security release scheduled for 2026-08-26 that includes a critical vulnerability fix for supported lines. The frontend is kept on the Next.js 16.3 release line so the patched 16.3.x can be resolved and locked once published. Do not treat the frontend dependency baseline as production-release-ready until that patch is installed, a lockfile is committed, and the security/build suite passes.

## DESIGN PATTERNS USED

- Modular Monolith
- Repository/Application-Service boundary conventions (documented for owning phases)
- Adapter/Strategy provider boundaries (ADR; implementation deferred to owning phases)
- Independent State Machine boundaries
- Transactional Outbox architecture decision (implementation deferred to first reliable event-producing phase)
- Global API envelope/error handling
- Deny-by-default security
- Test pyramid + Testcontainers integration foundation

## KNOWN LIMITATIONS

Execution-environment blockers, not hidden as successful verification:

1. Maven is not installed in the current runtime, so `mvn verify`, Spring Boot startup, and Java dependency resolution could not run.
2. Docker/Compose is not installed, so PostgreSQL/Testcontainers and clean-database Flyway execution could not run here.
3. Outbound npm registry DNS resolution fails with `EAI_AGAIN`, so frontend dependencies, `package-lock.json`, lint/typecheck/unit/build, Playwright browser installation, and E2E execution could not run here.
4. Because a lockfile could not be generated, CI currently uses `npm install`; once registry access is available, generate and commit `package-lock.json`, then switch CI/Makefile/Docker back to `npm ci` for reproducible installs.
5. The announced Next.js 2026-08-26 critical security patch must be installed and locked before a production release gate can pass.
6. The named Codex UI/UX skills are not exposed as callable tools in this runtime; the design-system requirements were implemented directly from the supplied build instructions and PRD. A later Codex run with those skills should audit the same tokens/patterns without expanding Phase 0 scope.

## PRD ITEMS COMPLETED

Phase-0/foundation portions completed in source:

- modular-monolith repository and module boundaries;
- PostgreSQL/Flyway migration ownership;
- API conventions and OpenAPI foundation;
- request/correlation IDs and logging foundation;
- liveness/readiness health checks;
- security-deny-by-default baseline and threat-model foundation;
- test infrastructure and CI definition;
- frontend architecture and design-system foundation;
- architecture decision records and conventions;
- Docker/Compose development topology definition;
- independent state-machine/readiness boundaries documented without implementing business logic.

## PRD ITEMS REMAINING

- All Phase 1–13 business functionality and its phase-specific migrations/tests/UI.
- Runtime verification of Phase 0 build, clean Flyway migration, startup, logs, and browser suite once a capable toolchain/network is available.
- Frontend lockfile plus the announced Next.js security patch.

## BUILD STATUS

**FAIL — NOT RUN TO COMPLETION because Maven is unavailable and npm dependency resolution is blocked by DNS/network restrictions. Structural config validation passed.**

## TEST STATUS

**FAIL — NOT RUN TO COMPLETION because Maven/Docker/npm dependencies are unavailable in this runtime. Test suites are present but must execute before the phase satisfies the Definition of Done.**

## READY FOR NEXT PHASE

**NO.**

Per the strict Definition of Done, Phase 1 should not begin until Phase 0 builds, tests, clean migrations, startup, log inspection, and Playwright validation pass in an environment with Maven, Docker, and npm registry access, and the announced Next.js critical security patch is applied.
