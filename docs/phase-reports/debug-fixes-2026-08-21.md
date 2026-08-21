# Debug and Production-Hardening Follow-up — 2026-08-21

## Scope

This pass re-audits the reviewed bundle against the PRD/SDLC requirements for tenant isolation, deterministic workflow correctness, page-bounded query behavior, production-safe configuration, validation, and release verification. It does not invent external provider credentials or weaken fail-closed production gates.

## Confirmed defects fixed

### Frontend/API correctness

- Fixed the API client discriminated-union bug by narrowing the success/error envelope before reading `error` fields.
- Added a metadata-preserving request path so paginated APIs can use backend `page`, `size`, `totalElements`, and `totalPages` instead of discarding them.
- Added a reusable accessible pagination control.
- Replaced client/project/service “load the first 100 then filter in React” behavior with bounded server-side search and real page navigation.
- Added stale-response sequence guards to client/project/service search so a slower older request cannot overwrite a newer search result.
- Project creation now searches clients and services server-side instead of silently limiting selectors to the first page; client/service loading indicators are tracked independently to avoid a concurrent-request loading-state race.

### Backend query and validation correctness

- Added optional bounded `query` parameters to client, project, and service list APIs.
- Search stays tenant-scoped at repository level and uses server-controlled escaped prefix matching; user `%`, `_`, and backslash characters are treated literally.
- Project search joins client/service rows with the same `organization_id`, keeping relationship search tenant-safe.
- Added Flyway `V16__server_side_search_indexes.sql` with tenant-prefixed expression indexes for the new prefix-search paths.
- Reminder-policy creation now maps invalid timezone/domain configuration to controlled HTTP 400 instead of allowing a domain exception to surface as HTTP 500.
- Notification-template creation now maps invalid domain configuration (for example PERMISSION scope without a required permission) to controlled HTTP 400.
- Added regression tests for both controlled-validation cases and extended the Phase 2 Testcontainers suite for paginated/tenant-scoped server search.
- Canonicalized security/business actor/template identifiers with `Locale.ROOT`; task audit actor type is now normalized before deriving nullable SYSTEM actor IDs, preventing a lowercase/whitespace SYSTEM actor from violating the database audit constraint.

### Build/deployment correctness

- The Next.js public API base URL is now supplied as a Docker **build argument**, avoiding the previous mistake of relying only on a runtime environment variable for a value Next.js embeds into the browser bundle at build time.
- Spring Boot parent is updated from 4.0.7 to 4.0.8 as dependency-maintenance hardening.
- TypeScript incremental metadata is excluded from source bundles.

## Static verification performed here

- `frontend/package.json`: JSON parse PASS.
- `backend/pom.xml`: XML parse PASS.
- Docker Compose and GitHub Actions YAML: parse PASS.
- Flyway migration names: contiguous V1 through V16.
- Migration inventory: 66 `CREATE TABLE` statements and 141 explicit index declarations; no duplicate index names detected.
- Java source parser pass with `javac`: no Java grammar diagnostics detected; dependency resolution prevents a real Maven compile in this environment.
- Modified frontend parser pass with global TypeScript: no syntax/parser diagnostics; zero unresolved local `@/` imports.
- Secret/risky-execution heuristics: no private-key/AWS/OpenAI/GitHub token signatures and no application `eval`, Java script engine, SpEL evaluator, `Runtime.exec`, or `ProcessBuilder` hits.
- No hard-coded `hasRole(...)`/ADMIN-role authorization pattern found in backend application code.

## Release gates intentionally still open

This environment does not provide Maven, Docker/PostgreSQL, project `node_modules`, or usable npm registry access, so it cannot truthfully prove:

1. `mvn -B -ntp verify` and the complete Testcontainers suite;
2. clean PostgreSQL Flyway V1 -> V16 plus Hibernate schema validation;
3. Spring Boot production-profile startup and runtime log inspection;
4. dependency installation/audit, ESLint, full semantic TypeScript checking, Vitest, and `next build`;
5. Playwright against a **real** Next.js + Spring Boot + PostgreSQL stack (the current browser regression specs predominantly mock APIs);
6. production-like `EXPLAIN (ANALYZE, BUFFERS)` and load/performance targets;
7. real approved payment and e-signature provider adapters/credentials.

The source must remain release-gated until those executable checks are green. Do not replace real provider adapters or full-stack testing with mock success paths simply to mark the release complete.

## Remaining defense-in-depth decisions

- Generate and commit `package-lock.json` in a registry-connected environment, then switch reproducible CI/Docker installation to `npm ci`.
- Enforce general API abuse throttling at the production gateway/WAF (and add application-level limits only for routes whose abuse model requires it); authentication/invitation throttling already exists in application state.
- A nonce/hash-based frontend CSP can further reduce `script-src 'unsafe-inline'`, but that migration should be validated against the deployed Next.js rendering model rather than changed blindly during static review.
- Run the real full-stack UAT journey from client/project creation through invitation, payment, contract, form, assets, access, review, READY, and permission-gated activation.
