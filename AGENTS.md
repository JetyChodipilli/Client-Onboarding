# AGENTS.md

## Source of truth

Read `docs/Client_Onboarding_PRD_SDLC_Implementation_Ready.docx` before changing product behavior. The PRD overrides assumptions. Approved ADRs explain implementation choices but do not change business rules.

## Execution discipline

- Implement only the phase explicitly requested by the user.
- Do not start the next phase automatically.
- Before implementation, inspect the current code, module map, ADRs and phase report.
- A feature is incomplete without validation, authorization, tenant-isolation, error-case and documentation coverage appropriate to its phase.
- Never mark a placeholder, fake provider, permissive security shortcut or TODO-based critical path as production-ready.
- Current implemented boundary: Phase 4. Do not introduce forms, assets, billing, contracts, access
  collection, generic notification/reminder infrastructure, activation, or reporting without an
  explicit request for the corresponding later phase.

## Backend rules

- Base package: `com.brainserve.clientonboarding`.
- Keep business logic out of controllers and persistence adapters.
- Controllers accept requests, invoke application use cases and map responses.
- Application services own use-case transactions.
- Domain code must not depend on Spring MVC, JPA entities from another module or integration SDKs.
- Modules communicate through explicit application ports, domain events or outbox events.
- No module may directly mutate another module's tables.
- Tenant-owned repositories require `organizationId`; never add an unscoped `findById` path for tenant data.
- Permission constants belong to the identity/organization authorization model, not role-name conditionals.
- Use Flyway for every schema change. Never edit an applied migration.
- Use optimistic locking and database constraints for meaningful concurrent updates.
- Use bounded pagination; default 50, maximum 100 unless a later approved requirement changes it.
- External callbacks require signature verification, provider-event uniqueness and idempotency before state mutation.

## Frontend rules

- Use feature-oriented folders. Shared primitives live under `components/ui`; cross-feature product components live under `components/shared`.
- Use semantic design tokens from `app/globals.css` and `design-system/client-onboarding-platform/MASTER.md`; do not add arbitrary raw colors in components.
- Use visible labels, clear recovery messages, keyboard focus, reduced-motion behavior and responsive layouts.
- Backend authorization remains authoritative.
- Client-portal screens must always expose current status, progress, next action, blocker, owner/waiting party, deadline and help.
- Keep “Your Action” visually and semantically separate from “Waiting for Our Team”.

## State boundaries

Never combine project, onboarding, onboarding-step, invoice, payment transaction, contract, form submission, asset, platform access or task states. `AWAITING_PAYMENT` is not a project status.

## Verification gate

Before phase completion:

1. Backend compiles and automated tests pass.
2. Flyway succeeds against a clean PostgreSQL database.
3. Frontend lint, unit tests and production build pass.
4. Relevant applications start and health checks pass.
5. UI phases pass Playwright at mobile, tablet and desktop widths with no unexplained console errors.
6. Authorization and tenant-isolation tests pass for all phase-owned resources.
7. README, OpenAPI, architecture docs and the phase report are updated.

## Phase 1 identity invariants

- Authenticated principals carry one current organization membership; session tokens contain no claims and are stored only as hashes.
- Role-name comparisons are forbidden. `@PreAuthorize` and application services use permission authorities.
- Organization/role/member repositories do not expose unscoped ID lookup paths for tenant-owned records.
- Privileged permissions require MFA; password changes revoke all sessions; invitations and recovery codes are single-use.
- Audit writes are append-only through the audit repository; normal API users have no mutation endpoint.

## Phase 2 core invariants

- Clients, contacts, services, projects, members, and activity rows are tenant-owned and always queried
  with `organization_id`.
- Archive operations preserve history; they do not hard-delete business records.
- A project belongs to exactly one client and one service. Those relationships cannot change after the
  project leaves `DRAFT`.
- Project lifecycle values never contain onboarding, payment, contract, asset, or access status values.

## Phase 3 workflow invariants

- Templates are mutable containers; published template versions and active onboarding snapshots are immutable.
- Conditions use the fixed field/operator/value DSL. Never execute user-authored expressions or scripts.
- Dependency graphs must be acyclic and contain only same-version edges. Runtime edges stay within one instance.
- Readiness means every applicable blocking step is `COMPLETED`; required and blocking remain separate flags.
- Step and onboarding lifecycles remain separate. Phase 3 creates instances in `DRAFT`; invitations and client
  portal transitions belong to Phase 4, while final approval/activation belongs to Phase 11.

## Phase 4 portal invariants

- `ClientContact` is business contact data; `ClientUser` is an authenticated principal. Never collapse them.
- Invitation secrets are opaque, hash-only at rest, expiring, single-use, revocable, and rotated on resend.
- Client sessions receive only `CLIENT_PORTAL_*` authorities and explicit project grants; they never inherit
  internal role permissions or unscoped client access.
- Portal reads derive organization/client/project scope from the authenticated principal and hide internal-only,
  inapplicable, and unassigned steps.
- Invitation delivery failure remains separate from authoritative invitation/onboarding state and can be retried.
- Phase 4 transitions onboarding `DRAFT` to `INVITED` and acceptance to `IN_PROGRESS`; forms begin in Phase 5.
- Project status must be `ONBOARDING` for invitations and step updates; lock its row during mutations so hold/cancel cannot race.
- Internal step transitions reject paused, expired, approved and terminal onboardings.
- Session refresh consumes an eligible current session exactly once, including requests authenticated before revocation.
- Role/member changes serialize last-manager checks using the organization row.
- Only active onboardings accept client step transitions. Review-required work is submitted, never self-approved.
- Invitation and portal list queries are paginated; progress reads use a bounded batch of onboarding instances.
- The live Phase 4 browser flow requires a fresh bootstrap database and a free loopback SMTP port 1025;
  use the CI browser job for repeatable full-stack verification.
