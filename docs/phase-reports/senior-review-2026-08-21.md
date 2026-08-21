# Senior Full-Bundle Review — 2026-08-21

## Review objective

Re-audit the complete Client Onboarding & Relationship Management Platform against the implementation-ready PRD/SDLC, with particular focus on client clarity, workflow completeness, multi-tenant security, transaction/concurrency correctness, performance/query design and professional UI quality.

The source-of-truth product outcome remains: the client should know what to do next, while the internal team can see what blocks readiness. The application must remain fast, structured, configurable, trackable, secure, automated and auditable.

## High-impact defects corrected

### 1. Review-required client task could bypass internal review

A client workflow task could be pushed directly to `COMPLETED`, and the workflow synchronization path could then complete a review-required step. The task command boundary now forbids direct client completion when the linked workflow step requires review. The client submits `IN_REVIEW`; only the internal path can complete a review-required workflow task.

A regression test source (`TaskWorkflowReviewSafetyTest`) now covers this security/business-rule boundary.

### 2. Task/workflow lock ordering was inconsistent

Task transitions previously locked the task before obtaining the onboarding aggregate lock. Other workflow handlers use onboarding -> feature-row locking, so the reversed order could deadlock under concurrent workflow/task updates. Workflow-generated task transitions now resolve and lock the onboarding context first, then acquire the task row lock.

### 3. Workflow-generated MANUAL_TASK had an impossible client configuration

The workflow builder defaulted manual tasks to client-visible even though workflow-generated manual tasks are internal/role-owned and have no client assignment. New validation rejects client-visible `MANUAL_TASK` definitions; the UI now defaults and locks them to internal visibility. Explicit client-assigned tasks remain a separate task capability.

### 4. APPROVAL semantics were ambiguous

`APPROVAL` is now enforced as an internal decision: not client-visible and not itself configured with a second `requiresReview` flag. The builder reflects the same constraints.

### 5. Several supported generic workflow step types had no completion path

`WELCOME`, `INSTRUCTION`, `EXTERNAL_LINK`, `VIDEO_GUIDE`, `MEETING`, `CUSTOM` and internal `APPROVAL` could become blocking requirements without a feature-specific command that completed them. A narrow `SimpleWorkflowStepService`/controller now provides controlled completion for only these generic step types.

Feature-owned types (`FORM`, `FILE_UPLOAD`, `PAYMENT`, `CONTRACT`, `PLATFORM_ACCESS`, `MANUAL_TASK`) are explicitly denied through this generic route, preventing domain-validation bypass. `SimpleWorkflowStepSafetyTest` covers the payment-bypass negative case.

### 6. Client next-action engine could point to an actionless requirement

The portal now chooses the primary next action only from step types that have an actual client action path. Legacy/unsupported client-visible workflow rows are categorized as waiting on the internal team rather than showing a disabled mystery CTA.

### 7. Client/internal onboarding UI did not handle generic steps

Project onboarding pages now expose valid completion/review actions for generic steps while preserving feature-specific screens for forms, files, billing, contracts and platform access. The new UI uses clearer progress treatment, next-action emphasis, error handling and responsive motion primitives.

### 8. Mobile client navigation hid core routes

The client portal now has a mobile bottom navigation with Overview, Tasks, Updates and Profile, including safe-area spacing and current-page semantics.

### 9. Required client Profile screen was missing

Added `/portal/profile` with the authenticated identity, organization/access scope, security explanation and password-reset recovery path. It does not expose internal tenant/user management controls.

### 10. Required Integrations screen was missing

Added a credential-safe `/app/integrations` operational view backed by `GET /api/v1/integrations`. It reports provider/capability readiness for payments, e-signature, private storage, malware scanning and transactional email while intentionally never returning provider secrets.

The existing production startup gate still rejects disabled/sandbox payment and e-signature adapters.

### 11. Frontend visual hierarchy/navigation was too flat

The internal sidebar is now grouped into Workspace, Build, Operations, Engagement, Insights and Administration. Client/internal pages use a consistent design system for metrics, primary actions, status feedback, cards and subtle page entry transitions. Reduced-motion users receive no decorative motion.

### 12. Frontend CSP was minimal

The Next.js security policy now declares `default-src`, script/style/image/font/connect/worker/manifest boundaries in addition to frame/base/object/form protections. Development-only eval/websocket allowances are excluded from the production policy where possible.

### 13. Frontend direct dependency versions were floating

Direct npm dependency/devDependency versions are pinned exactly in `package.json` to reduce uncontrolled drift. A true npm lockfile is still required for full transitive reproducibility and must be generated/committed in a registry-connected verification environment.

### 14. Non-review workflow tasks could strand their workflow step in SUBMITTED

The task synchronizer treated every task-level `IN_REVIEW` as a workflow-level submission. For a MANUAL_TASK whose workflow step did **not** require review, the workflow could move to `SUBMITTED` and never reach `COMPLETED` when the task later completed. Task-level review and workflow-level review are now separated: non-review workflow steps stay `IN_PROGRESS` while the task is internally reviewed, then complete when the task completes; review-required steps still follow `SUBMITTED -> UNDER_REVIEW -> COMPLETED`.

### 15. Internal generic steps with requires-review could not finish

An internal-only generic step configured with `requiresReview=true` could enter `IN_PROGRESS` but the generic handler had no path to `SUBMITTED/UNDER_REVIEW`. The internal generic command now supports the complete two-stage path and the UI labels the first action as **Submit for review**, followed by **Approve requirement**.

## ACID and concurrency review

The existing architecture already uses the right production-oriented consistency model:

- application-service transaction boundaries for atomic business operations;
- PostgreSQL as the authoritative state store;
- transactional outbox for commit-coupled asynchronous work;
- optimistic `@Version` locking on mutable aggregates;
- targeted `PESSIMISTIC_WRITE` row locking for concurrency-sensitive transitions/idempotency;
- provider event uniqueness/idempotency for external callbacks;
- explicit state machines rather than status overloading.

The task lock-order correction in this review aligns a previously inconsistent command path with the canonical onboarding-first lock order.

## Database/index review

Flyway now remains contiguous `V1` through `V15`. V15 adds only concrete query-path indexes for task ordering, notification feeds/admin lists, scheduled reminders, overdue invoice scanning and outbox routing/retry recovery; no business tables were duplicated. The reporting layer has query-path indexes in V13; task, workflow, tenant, status, due-date, provider/external-ID and relationship paths are also indexed in their owning migrations.

No speculative cache was added. V15 indexes map directly to existing repository/worker order/filter paths. Additional indexes still require `EXPLAIN (ANALYZE, BUFFERS)` evidence because every index has write/storage cost.

## Cache design

No unsafe Redis cache was introduced. The performance pass instead adds pgjdbc prepared-statement reuse/batch rewrite, Hibernate batching/fetch-size guards, concrete indexes, and notification fan-out query reduction. The PRD explicitly says Redis only when justified, and mutable authorization/workflow/financial/readiness data is dangerous to serve stale. The repository now includes `docs/architecture/cache-policy.md`, defining safe cache candidates, tenant-aware keys, invalidation requirements and an adoption gate based on observed performance metrics.

## Static security/quality evidence collected in this environment

- Java main+test grammar parse: 601 files, zero parser errors.
- TypeScript/TSX grammar parse: zero syntax diagnostics and zero heuristic unused imports.
- `@/` alias import resolution: zero missing local imports.
- Secret-signature scan: zero private-key/AWS/OpenAI/GitHub-token signatures.
- Risky execution scan: zero arbitrary `eval`, Java script engine, SpEL parser, `Runtime.exec` or `ProcessBuilder` hits in application source.
- Hard-coded admin-role-check heuristic: zero hits.
- Non-global bare `.findById(...)` tenant-access heuristic: zero hits.
- `frontend/package.json`: JSON parse OK.
- `backend/pom.xml`: XML parse OK.
- Spring/Compose/GitHub workflow YAML: parse OK.
- Flyway migrations: versions 1–14 contiguous.
- Migration inventory: 66 tables, 137 indexes.

Static inspection of intentionally aggregate-bounded list repository methods found no new release blocker; those methods are bounded by a specific aggregate, user, or an input ID collection derived from bounded queries.

## Executable verification still required

This sandbox does not contain Maven, Docker/PostgreSQL, a populated frontend `node_modules`, or a usable npm registry connection. Therefore the following cannot truthfully be declared green here:

- full Java type compilation and `mvn verify`;
- Testcontainers/PostgreSQL integration/security tests;
- clean Flyway V1→V14 application against PostgreSQL + Hibernate validation;
- Spring Boot production startup/log inspection;
- npm dependency install/audit and lockfile generation;
- ESLint/full TypeScript semantic typecheck/Vitest/Next production build;
- Playwright browser execution across desktop/tablet/mobile.

The repository intentionally keeps production readiness **closed** until those gates execute successfully.

## Deliberate production blocker

The repository still contains only disabled/signed-sandbox payment and e-signature adapters. Production startup is designed to reject them. A real approved payment adapter and e-signature adapter plus production secret/configuration evidence must be supplied before release. Choosing a vendor is a product/deployment decision and was not silently invented during this review.

## Overall assessment

The source now aligns substantially better with the PRD motto and full onboarding journey. The most important workflow/UI/security defects found in this review have been corrected. Source/static validation is clean for the checks above, but "zero errors" and "production ready" cannot be certified until the mandatory executable build, migration, browser, security and provider gates run in a capable environment.
