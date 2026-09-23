# Phase 4: Client invitation and portal

## Boundary

Phase 4 adds the capability boundary between internal onboarding operators and client participants. A
`ClientContact` remains editable business contact data. A `ClientUser` links an authenticated `CLIENT` principal
to one client and may receive explicit grants to one or more projects. Invitation acceptance creates that link;
it never turns the contact row into an identity record.

## Invitation lifecycle

Invitation creation requires `ONBOARDING_INVITE` and an MFA-assured internal session. The contact, client,
project, and onboarding are joined under the authenticated `organization_id`. The service issues a
cryptographically random secret, stores only its SHA-256 hash, records expiry, and transitions the onboarding
from `DRAFT` to `INVITED`. Resend replaces the hash and expiry; revoke and accept are optimistic, terminal
mutations. Acceptance is single-use and transitions `INVITED` to `IN_PROGRESS`.

The SMTP attempt occurs after the authoritative invitation record exists. Delivery status (`PENDING`, `SENT`,
or `FAILED`) is tracked separately, and a delivery failure leaves resend available without rolling back the
invitation or onboarding state.

## Client authorization

Client login uses the same opaque, revocable server-side session store but resolves a separate client access
record. Client sessions receive only `CLIENT_PORTAL_*` authorities. Every portal query uses the authenticated
organization and client-user identifier and joins through `client_user_project_access`; request paths cannot
select another tenant or ungranted project. Client administrators see all visible steps in granted projects.
Client members see only generally assigned or `CLIENT_MEMBER` steps.

## Portal read model

The portal returns the project/onboarding status, progress across the authenticated client's visible assigned
steps, next action, blocking reason,
waiting party, the next action's deadline, help route, and the ordered client-visible applicable steps.
Internal-only, condition-disabled, and unassigned steps are never serialized. Locked steps name only visible
prerequisites; otherwise they say the project team is completing a prerequisite.

Phase 4 can start and complete only informational step types (`WELCOME`, `INSTRUCTION`, `EXTERNAL_LINK`, and
`VIDEO_GUIDE`). Form, file, payment, contract, access, and other typed handlers remain unavailable until their
own phases and return an explicit recovery message instead of a fake completion path.

Informational steps requiring review submit to `SUBMITTED`; the client cannot approve its own submission.
Paused, expired, cancelled, completed, and other non-active onboardings reject client step updates. The same
lifecycle checks prevent accepting or resending invitations after onboarding is closed or paused. Invitations
to archived, completed, or cancelled projects cannot activate access.

## Query and error boundaries

Project and invitation lists accept zero-based `page` and `size` (default 50, maximum 100). The UI exposes
previous/next navigation when needed. Project progress loads all onboarding steps for the current page in
two batched queries rather than per-project queries. The batch is bounded to 100 onboarding instances, each
with the existing 200-step template ceiling. Progress includes only requirements visible to the caller.

Invalid parameter constraints use the shared `VALIDATION_FAILED` HTTP 400 envelope. Client login, invitation
password confirmation, and password-reset requests use the existing bounded rate limiter. Production ingress
still needs distributed rate limiting when multiple application instances are used.

## Verification and rollout

`Phase4IntegrationTest` uses a fresh embedded PostgreSQL instance for invitation persistence, rotation,
single-use activation, permission rejection, tenant separation, CSRF, optimistic conflicts, delivery failure,
member visibility, review submission, paused lifecycle, and pagination constraints. Existing H2, PostgreSQL
Testcontainers, architecture, readiness, and application-startup gates remain enabled.

The CI browser job runs real bootstrap MFA, creates a client/project/workflow through authorized APIs, captures
the production SMTP adapter's message in a loopback test inbox, and follows the invitation in a separate browser
context through activation, login, completion, logout, and consumed-link rejection. UI error and responsive cases
use controlled API responses across four viewports. Browser reports and screenshots are retained for 14 days.

Apply V6 with Flyway before serving the Phase 4 application. V1–V5 are unchanged. Do not roll back by dropping
identity or invitation data; restore the tested backup or forward-fix if the release must be reverted. SMTP must
be configured for the environment. A failed delivery remains visible to an authorized operator for resend.

## Cross-phase debug review

Invitation creation, resend and acceptance require a project in `ONBOARDING`. Client step updates
require both that project state and onboarding `IN_PROGRESS`. Internal transitions additionally reject
paused, expired, approved and terminal onboarding states. The project module exposes a row-locking
application port, held for the mutation transaction, so project hold/cancel and step updates serialize.
The separate lifecycle values are retained; holding a project does not silently rewrite onboarding state.

Held/cancelled projects remain readable until archive, with no client action, a status explanation and
team contact. A visible prerequisite awaiting review belongs to `OUR_TEAM`; an actionable visible
prerequisite belongs to `YOUR_ACTION`. Hidden prerequisite names remain private.

Both shells show logout failure and retain a retry action until the server confirms logout or session
absence. Session rotation atomically consumes the old session and checks expiry and credential version.
See [the audit report](../phase-reports/phases-0-4-debug-audit.md) for regression evidence.
