# Phase 5 — Forms & Questionnaires

PHASE COMPLETED:

Phase 5 — Forms & Questionnaires. Phase 6 was not started.
Delivery is through [PR #27](https://github.com/JetyChodipilli/Client-Onboarding/pull/27), targeting
`main` after the merged Phases 0–4 audit. No deployment or Phase 5 merge is claimed here.

IMPLEMENTED:

- Tenant-owned reusable forms, ordered fields, draft versions and immutable published versions.
- Safe conditional questions, type/length/format validation, incomplete draft saving and final submission.
- Project-scoped client responses, internal review, revision feedback, resubmission and approval.
- Immutable submission/review history, optimistic conflicts, concurrent-command protection and audit events.
- Published form versions pinned to workflow snapshots; dedicated FORM transitions, skip/reopen rules,
  dependency refresh and readiness recalculation in the same transaction as response writes.
- Responsive builder, preview, client questionnaire and reviewer screens using the existing design system.
- Removed the remaining H2 test dependency: identity/foundation integration tests now use PostgreSQL.
- Updated README, AGENTS, architecture/module map, OpenAPI, CI and the reproducible verification script.

DATABASE MIGRATIONS:

- Added `V7__forms_and_questionnaires.sql`; V1–V6 are unchanged.
- Added forms, form_versions, form_responses, form_submissions, form_reviews and form_outbox_events.
- Composite tenant foreign keys, indexes, one-draft uniqueness, bounded payloads and immutable-history triggers.
- Clean PostgreSQL migration from V1 through V7 and application Flyway startup pass in backend CI.

API ENDPOINTS:

18 operations across 16 new paths under `/api/v1`:

| Area | Routes |
|---|---|
| Templates | `GET/POST /forms`; `GET /forms/{id}`; `POST /forms/{id}/archive` |
| Versions | `GET/POST /forms/{id}/versions`; `GET /form-versions/{id}`; `PUT /form-versions/{id}/fields`; `POST /form-versions/{id}/publish` |
| Internal responses | `GET /form-responses/{stepId}` and `/submissions`; `POST /review`, `/skip`, `/reopen` below that response |
| Client responses | `GET /client-portal/projects/{projectId}/forms/{stepId}` and `/submissions`; `PUT /draft`; `POST /submit` below that response |

The static OpenAPI document contains 73 paths; local references resolve. Runtime OpenAPI includes
form template and review endpoints and is probed by the application smoke test.

UI SCREENS:

- `/app/forms`: searchable, paginated template list and creation.
- `/app/forms/[formId]`: field builder, preview, versions, publish and archive.
- `/app/forms/responses/[stepId]`: review, revision, history and permitted exception actions.
- `/portal/projects/[projectId]/forms/[stepId]`: conditional questionnaire, drafts, submission and history.
- Existing workflow/project/portal screens link to the dedicated form flow.
- Loading, empty, denied, validation, API failure, stale-write and read-only states are explicit.

TESTS ADDED:

- Five domain policy tests and nine PostgreSQL form integration tests.
- Coverage includes hidden/required fields, numeric equality, boolean false, formats/options, version pinning,
  immutable history, review/revision/resubmit, automatic completion without review, skip/reopen, tenant and
  project grants, assignment, CSRF, stale versions, paused/held/closed work and concurrent commands.
- Four frontend tests cover visibility, numeric/prototype-name edge cases, accessible validation and read-only controls.
- Existing migration, architecture, security and runtime smoke tests remain enabled.
- Verified totals: 69 backend tests and 16 frontend unit tests, with zero failures or backend skips.

PLAYWRIGHT SCENARIOS:

- Five form scenarios run at 1440×1000, 768×1024, 375×812 and 812×375.
- Draft saving, conditional questions, submission, field errors, preserved conflict input, reviewer revision,
  publishing/preview, loading/empty/denied states, overflow and console checks.
- The live bootstrap/MFA/SMTP invitation journey now continues through draft persistence, review,
  revision, resubmission, approval, immutable history and 100% project progress.
- 109 browser scenarios executed successfully, with three intentional duplicate-bootstrap skips.
  The linked implementation run passed 108 immediately and one older workflow assertion on retry;
  that ambiguous heading selector has been corrected. Final-revision checks are available on PR #27.
- Three repetitions of the live bootstrap scenario are intentionally skipped outside desktop; the full
  live journey executes once against a fresh database, while responsive form scenarios run on all viewports.

SECURITY VALIDATION:

- Tenant-scoped repository access and composite foreign keys; foreign reads/writes return secure 404s.
- Missing authentication, insufficient permissions, invalid CSRF, foreign project grants, hidden and
  unassigned steps are rejected. Client principals cannot review or use internal administration APIs.
- FORM_MANAGE and FORM_REVIEW require MFA; existing roles receive no automatic privilege elevation.
- Project row locks plus expected response versions serialize save/review/hold/cancel operations.
- Duplicate submissions produce one success and one conflict, with one snapshot and one event.
- Generic workflow transitions cannot bypass questionnaire validation or approval.
- Conditions cannot execute scripts; answers render as text. URL answers are validated but never fetched.
- Audit/outbox metadata exclude answers. Production cookie/session/CORS/CSRF safeguards remain in force.

DESIGN PATTERNS USED:

- Modular monolith, application services, tenant-scoped repository and dependency-inverted workflow validator.
- Versioned immutable definitions and submission snapshots; explicit state policies and optimistic locking.
- PostgreSQL row locks/constraints/triggers and transactional producer-owned outbox records.
- Shared native field renderer and existing API/UI components; no new dependency or generic provider framework.

KNOWN LIMITATIONS:

- File fields and secure uploads belong to Phase 6; outbox delivery workers belong to Phase 10;
  final onboarding approval/project activation belongs to Phase 11.
- Legacy FORM snapshots without a published formVersionId remain unavailable. New workflow versions
  can bind forms; existing immutable snapshots require a separately approved migration if needed.
- Existing organizations must explicitly grant FORM_READ, FORM_MANAGE and/or FORM_REVIEW in role settings.
- Per-instance login throttling still requires a distributed production ingress policy.
- This phase does not claim Phase 13 penetration, load or production-readiness certification.

PRD ITEMS COMPLETED:

- Form templates and versions; fields and conditions; draft responses and validated submission;
  review, revision requests, resubmission and approval; workflow state/readiness integration.
- Phase-owned permissions, tenant isolation, immutable traceability, UI states, automated tests and documentation.

PRD ITEMS REMAINING:

- Phases 6–13 remain outside this implementation. No next phase starts automatically.

BUILD STATUS:

PASS — Maven verification, Next.js lint/typecheck/production compilation and both container builds.

TEST STATUS:

PASS — 69 backend tests, 16 frontend tests, browser regression and clean Compose startup passed.
The implementation-run retry is disclosed above; the corrected selector is included in the final revision.

READY FOR NEXT PHASE:

YES — after normal PR review/merge. Phase 6 requires its own instruction.

## Verification evidence

Implementation revision: `46a9820506661877efa0c47a2b9f70381623e2a8`.
[CI run 35950225127](https://github.com/JetyChodipilli/Client-Onboarding/actions/runs/35950225127).
All four jobs passed. The container job built both production images, applied V1–V7 to a clean
PostgreSQL database, started all services, probed backend readiness and client sign-in, and found
no unexplained ERROR entries in the container logs. The browser job ran the live full-stack journey
successfully. The final selector/documentation revision must pass the same PR checks before delivery;
see PR #27 for that exact revision's results.

See also the [architecture/rollout notes](../architecture/phase-5-forms-questionnaires.md) and
[UI/Ponytail review](phase-5-design-audit.md).
