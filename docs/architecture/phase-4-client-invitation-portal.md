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
waiting party, nearest applicable deadline, help route, and the ordered client-visible applicable steps.
Internal-only, condition-disabled, and unassigned steps are never serialized. Locked steps name only visible
prerequisites; otherwise they say the project team is completing a prerequisite.

Phase 4 can start and complete only informational step types (`WELCOME`, `INSTRUCTION`, `EXTERNAL_LINK`, and
`VIDEO_GUIDE`). Form, file, payment, contract, access, and other typed handlers remain unavailable until their
own phases and return an explicit recovery message instead of a fake completion path.
