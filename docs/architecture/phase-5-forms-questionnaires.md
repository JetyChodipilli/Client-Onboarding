# Phase 5: Forms and questionnaires

## Scope and ownership

The forms module owns reusable templates, draft/published definitions, draft responses, immutable
submission snapshots, append-only reviews, and durable form events. It uses the existing portal
application boundary for client/project grants and the onboarding application boundary for step
updates. It never writes portal, project, or onboarding tables directly. ArchUnit verifies acyclic
modules and keeps persistence out of controllers.

`StepConfigurationValidator` is the workflow-owned extension point. Forms validates the selected
`configuration.formVersionId` when a workflow is published and again when onboarding starts.
`StepExecutionService` owns workflow state/readiness writes within the calling feature transaction.
The dependency direction remains forms → portal/onboarding/workflow; those modules do not import forms.

## Versioning and persistence

`forms` contains the reusable name and description. `form_versions` contains ordered field definitions
as bounded JSON, a version number and optimistic lock. Published definitions cannot be updated or
deleted, including through SQL. The workflow snapshot pins the published form version identifier;
new form versions do not change existing onboarding questions. Archiving prevents future selection
and publication while existing responses and history remain available.

`form_responses` has one row per tenant and step, with the current draft, independent response state,
submission number, feedback and optimistic version. A read of an unanswered form returns an empty
draft with version 0 and does not write data. The first successful save or submit creates version 1.
`form_submissions` stores each submitted answer snapshot. `form_reviews` appends review decisions and
feedback. Both history tables reject updates/deletes through PostgreSQL triggers. Fields and answers
are bounded JSON documents rather than additional join tables: definitions/submissions are read and
validated as a whole, with no field-level querying requirement in this phase.

All relationships use composite organization/resource foreign keys. Lists are tenant-scoped and
paginated; submission pages are limited to 20 answer-bearing records. Reviews are loaded in a batch,
not one query per submission. Answers and definitions each have a 100,000-character storage ceiling.

## Fields and conditions

Supported types: text, textarea, number, email, URL, date, dropdown, radio, checkbox groups,
multi-select and boolean. Text has a configurable limit (default 2,000; maximum 10,000); choices use
1–50 unique options; numeric fields support minimum and maximum. A form has 1–100 unique field keys.
File collection belongs to Phase 6 and cannot be configured as an insecure form upload here.

Conditions use only `fieldKey`, `operator`, and `value`. They reference an earlier field, preventing
cycles. Scalar conditions use EQUALS/NOT_EQUALS; multiple-choice conditions use CONTAINS. Numeric
equality ignores decimal scale. Missing/hidden answers never enable later fields. Hidden answers
are removed on save/submission; unknown answer keys are rejected. No scripts, supplied regexes,
HTML rendering or URL fetching occur.

Drafts may omit required answers or contain unfinished email/date/URL text; they still enforce
types, length limits and configured options. Submission validates all visible required fields and
formats. Boolean false is a valid answer. Client and server share these visibility semantics, with
the server authoritative and field-linked errors returned through the standard envelope.

## State transitions and concurrency

| Form action | Response state | Workflow step |
|---|---|---|
| Save draft | DRAFT | IN_PROGRESS |
| Submit when review is required | SUBMITTED | SUBMITTED |
| Start review | UNDER_REVIEW | UNDER_REVIEW |
| Request revision, with feedback | NEEDS_REVISION | NEEDS_REVISION |
| Save revision | DRAFT | IN_PROGRESS |
| Resubmit | SUBMITTED, new immutable submission | SUBMITTED |
| Approve or submit a form without required review | APPROVED | COMPLETED |
| Authorized reopen, when configured, with reason | DRAFT, history retained | IN_PROGRESS |
| Authorized skip, when configured and non-blocking | Draft retained | SKIPPED |

Clients cannot review, approve, skip or reopen responses. Internal generic step actions reject FORM
steps so they cannot bypass validation or leave response state inconsistent. Reopen/skip require
FORM_REVIEW, a reason, and the original workflow rules. Submitted answers remain immutable even
when the response is reopened.

Mutations first resolve tenant/client/project/assignment scope, lock the project through its
application port, reread current state, and require project ONBOARDING plus onboarding IN_PROGRESS.
This serializes saves, reviews and project hold/cancel. The expected response version must still match;
a stale or duplicate command returns a controlled 409 with no partial persistence. Response, submission,
review, dependency availability, readiness, audit and relevant outbox records commit in one transaction.

Readiness remains “every applicable blocking step is COMPLETED.” This phase does not perform final
onboarding approval, project READY, or activation; those remain Phase 11.

## Authorization, audit and events

FORM_READ reads internal definitions/responses; FORM_MANAGE manages templates and publishing;
FORM_REVIEW reads responses and performs review/exception actions. FORM_MANAGE and FORM_REVIEW
are privileged permissions requiring MFA assurance. Existing roles receive no automatic elevation:
use Settings → Roles with ROLE_MANAGE to assign the new permissions. Fresh bootstrap roles include
the registered permissions.

Clients use CLIENT_PORTAL_READ/CLIENT_PORTAL_STEP_UPDATE, explicit project grants and existing
assignment rules. Hidden, inapplicable, unassigned and foreign-project steps return secure 404s.
Cookie mutations require CSRF. Draft answers are retained on failed saves/conflicts in the UI;
discard/reload and navigation warnings are explicit.

Audit records contain state, submission number and identifiers, never answer contents. Form submit,
review, revision, approval, skip and reopen append `form_outbox_events` with event UUID, tenant,
response aggregate, submission number, timestamp, correlation ID and payload version. These are
durable producer records; Phase 10 adds delivery workers, retry and suppression. No notification
delivery is claimed in Phase 5.

## Rollout and limitations

Apply V7 through Flyway; V1–V6 remain unchanged. The PostgreSQL-specific immutability/partial-index
rules are tested on PostgreSQL, including the identity/foundation tests previously using H2.
Do not reverse this migration by dropping responses or history; use a forward fix after backup.

Existing legacy FORM workflow steps without a published `formVersionId` remain explicitly
unavailable. Create a new correctly configured workflow version for new onboardings. Existing
snapshots are never silently rewritten; migrating a legacy active snapshot needs a separately
approved data migration. File fields/uploads, notification delivery and final activation are outside
this phase. Per-instance authentication throttling still needs a distributed production ingress policy.

## Verification

`FormPolicyTest` covers validation and safe conditional visibility. `Phase5IntegrationTest` exercises
the real invitation/session path, drafts, validation, revision/resubmission, immutable history,
review concurrency, tenant/permission/project/assignment isolation, CSRF, held projects, optimistic
conflicts, skip/reopen rules and transactional events. Existing architecture, migration and runtime
smoke tests stay enabled. Playwright covers all four existing viewports and extends the live SMTP/MFA
client onboarding journey through questionnaire approval.
