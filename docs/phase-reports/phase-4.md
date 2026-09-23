# Phase 4 — Client Invitation & Portal

PHASE COMPLETED:

Phase 4 — Client Invitation & Portal. Implementation and required verification passed. Phase 5 was not started.

Repository: [Client-Onboarding](https://github.com/JetyChodipilli/Client-Onboarding/tree/codex/phase-4-client-portal).
The work is isolated on `codex/phase-4-client-portal`, based on verified Phase 3 commit
`8d2f8e0a5de03a5aba80a92f28220d9fee09b383`. Existing later-phase work on `main` was preserved.
No merge, deployment, or source ZIP is part of this handoff.

IMPLEMENTED:

- Reverified previous-phase builds, architecture boundaries, readiness/state policies, authorization, migrations,
  and startup. Corrected shared parameter-validation handling so invalid request parameters return HTTP 400.
- Secure client invitations with cryptographic secrets, hash-only storage, expiration, single-use acceptance,
  resend rotation, revoke, idempotent creation, optimistic versions, and separate delivery status.
- Contact-to-client-account activation with explicit project grants and separate internal/client authority scope.
- Client login, password recovery, session refresh/logout reuse, lockout and rate-limit enforcement.
- Responsive portal project list and dashboard with current status, progress, next action, blocking reason,
  waiting party, deadline and help; internal and client actions are visibly separate.
- Informational step start, completion, review submission, and revision; paused/closed lifecycle guards and
  protection against client self-approval or bypass of dedicated evidence handlers.
- Bounded invitation/project pagination and batched progress reads.
- Updated README, AGENTS, architecture, threat model, OpenAPI, verification script, CI and visual audit.

DATABASE MIGRATIONS:

- Added `V6__client_invitations_and_portal.sql`; V1–V5 are unchanged.
- Added invitation records, client role/activation data, explicit project access and `ONBOARDING_INVITE` permission.
- Composite tenant/client/project foreign keys, hashed-token uniqueness, pending-invitation uniqueness and status
  constraints protect relationships and concurrent commands.
- Clean embedded PostgreSQL, PostgreSQL Testcontainers and container startup all migrated successfully through V6.

API ENDPOINTS:

- `GET/POST /api/v1/onboardings/{onboardingId}/client-invitations`
- `POST /api/v1/client-invitations/{invitationId}/resend`
- `POST /api/v1/client-invitations/{invitationId}/revoke`
- `POST /api/v1/client-invitations/inspect`
- `POST /api/v1/client-invitations/accept`
- `POST /api/v1/client-auth/login`
- `POST /api/v1/client-auth/forgot-password`
- `GET /api/v1/client-portal/projects`
- `GET /api/v1/client-portal/projects/{projectId}`
- `POST /api/v1/client-portal/projects/{projectId}/steps/{stepId}/transition`
- Existing CSRF, current-user, reset-password, session-refresh and logout endpoints serve client sessions safely.

UI SCREENS:

- Internal project invitation controls, resend and confirmed revoke.
- Client invitation verification/activation and unavailable-link states.
- Client sign-in, forgot password and reset password.
- Project list, dashboard, requirements, review/revision actions, and help.
- Loading, empty, validation, denied, expired-session and API-error states.

TESTS ADDED:

- Three client-step policy unit tests cover every non-actionable state, forbidden targets, review enforcement,
  revision and exclusion of evidence-collection handlers.
- Seven PostgreSQL integration tests cover invitation lifecycle, tenant/permission/CSRF boundaries, idempotency,
  token rotation and reuse, delivery failures, member visibility, explicit grants, review, paused/terminal states,
  optimistic conflicts and pagination validation.
- Backend total: **46 passed, 0 failed, 0 skipped**.
- Frontend total: **8 unit tests passed**; ESLint, TypeScript and production build passed.

PLAYWRIGHT SCENARIOS:

- **85 passed; 3 intentionally skipped** of 88 discovered cases.
- Four viewports: desktop 1440×1000, tablet 768×1024, mobile portrait 375×812, landscape 812×375.
- Invitation acceptance/expiry, client login, dashboard hierarchy, progress, locked reasons, deadlines/help,
  informational actions, review submission, loading, empty and API-error states, and internal send/resend/revoke.
- Full live flow: bootstrap MFA → API-created client/project/workflow → production SMTP adapter delivery to a
  loopback test inbox → separate client browser activation/login → completion → internal API denial → logout →
  session rejection → consumed invitation rejection. No API mocking is used in this live flow.
- The live MFA flow runs once on desktop against a fresh database; its three viewport duplicates are skipped.
- Existing keyboard, theme, reduced-motion, console-error and overflow regression scenarios remain enabled.
- Captured screenshots were inspected in the [design audit](phase-4-design-audit.md).

SECURITY VALIDATION:

- Missing authentication returns 401, missing permission returns 403, cross-tenant or ungranted resources return 404.
- Internal invitation operations require permission and session MFA assurance. Client sessions receive only
  `CLIENT_PORTAL_*` authorities and never inherit internal RBAC permissions.
- Client members cannot read or mutate admin-assigned or hidden internal steps. Project grants are authoritative.
- Invitation secrets are not returned to operators or stored in plaintext. Resend invalidates the old link;
  expiry, revoke and acceptance prevent reuse. CSRF and DTO validation remain enforced.
- Review-required work cannot be self-approved. Locked, paused and closed-state transitions are rejected.
- PostgreSQL constraints and optimistic updates protect concurrent acceptance, revoke and step changes.
- SMTP failure tests produce expected warning logs; no unexplained runtime errors remained in the verified run.
- Container readiness and client-page probes passed, with no application ERROR entries during clean startup.

DESIGN PATTERNS USED:

- Modular monolith with enforced module boundaries.
- Thin controllers, transactional application services and tenant-scoped repositories.
- Existing notification/session ports and SMTP adapter; separate pure client-transition policy.
- Opaque capability tokens, explicit project grants, optimistic locking, idempotent commands and append-only audit APIs.
- Feature-oriented UI using the existing semantic design system, with no added UI runtime dependency.

KNOWN LIMITATIONS:

- Rate limiting remains per application instance; multi-instance production ingress needs distributed enforcement.
- Production SMTP configuration and deliverability are deployment concerns; the verified flow uses a local test inbox.
- Forms, files, payments, contracts, access collection and final activation require their later phases; the portal
  does not simulate those handlers. Generic delivery retry/reminder infrastructure remains Phase 10 scope.
- This branch was pushed for review, not merged into the separately advanced `main` branch or deployed.

PRD ITEMS COMPLETED:

- Secure invitation generation, expiration, resend, revoke and single-use acceptance.
- Client account activation and project/tenant/role authorization.
- Client dashboard, progress, next action, locked-step explanations, ownership, deadlines and help.
- Required test, migration, startup, browser, security and documentation gates for Phase 4.

PRD ITEMS REMAINING:

- No remaining Phase 4 implementation gate.
- Phases 5–13 remain outside this branch's authorized scope.

BUILD STATUS:

PASS — backend package, frontend production build, both Docker images and full Compose startup.

TEST STATUS:

PASS — all executable mandatory tests passed; only the three intentional live-flow viewport duplicates are skipped.

READY FOR NEXT PHASE:

YES — Phase 5 requires a separate instruction.

## Verification evidence

[CI run 35719101017](https://github.com/JetyChodipilli/Client-Onboarding/actions/runs/35719101017)
verified commit `56b6af386cef497d198d445469faf2b86c16c527`: backend, frontend, browser regression and container
build/startup jobs all passed. Subsequent handoff changes contain the audit report and small portal text/wrapping
polish; the branch's CI workflow reruns the same full gate after publication.
