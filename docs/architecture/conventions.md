# Engineering Conventions

## Database

- PostgreSQL is authoritative; Flyway owns all production schema changes.
- UUIDs are primary identifiers unless an ADR documents a stronger reason.
- Tenant-owned records include `organization_id`, audit timestamps/actors and an optimistic-lock `version` where concurrent mutation matters.
- Foreign keys, tenant-scoped unique constraints and indexes follow actual query patterns.
- Referenced business records are archived/soft-deleted; legal and financial records are not physically deleted through normal UI paths.
- Timestamp storage is UTC (`timestamptz`); presentation uses the user's locale/time zone.
- Migration names: `V<sequence>__<lower_snake_description>.sql`; applied migrations are immutable.

## API

- Base path: `/api/v1`.
- JSON property naming: lower camel case; enum wire values: uppercase snake case.
- Success envelope: `success`, `data`, `meta`, `requestId`.
- Error envelope: `success`, `error.code`, `error.message`, `error.fieldErrors`, `requestId`.
- Default page size 50; maximum 100; page index starts at zero.
- Sorting uses `field,direction`; unknown fields are rejected rather than interpolated.
- Duplicate-sensitive commands accept `Idempotency-Key` when their phase is implemented.
- Stale optimistic-lock updates map to HTTP 409 with a stable domain error code.
- Foreign-tenant lookups return the approved secure 404/authorization response without leaking existence.

## Security

Every protected request follows this order:

```text
authenticated actor
→ resolved organization membership
→ required permission
→ tenant-scoped resource lookup
→ parent/child relationship validation
→ validated command
```

- DTO allowlists prevent mass assignment.
- Repositories expose tenant-scoped methods for tenant data.
- Logs exclude secrets, raw reset/invitation tokens, authorization headers and sensitive provider payloads.
- Uploads use authorized presigned operations, MIME/signature policy, scanning and quarantine.
- Webhooks verify signature and provider event uniqueness before any business mutation.
- Audit records are append-oriented and immutable to normal users.
- Session cookies contain random opaque values only; PostgreSQL stores SHA-256 token hashes.
- Credential changes increment `credential_version` and revoke every active session.
- Cookie-authenticated mutations require `X-XSRF-TOKEN`; frontend permission checks remain advisory.
- Privileged permission sets require TOTP MFA. Recovery codes are hashed, single-use, and shown once.

## Testing

- Unit tests: policies, calculations, state transitions and handlers.
- Slice tests: controllers, validation, serialization and persistence adapters.
- Integration tests: PostgreSQL/Flyway, transactions, constraints, authorization and tenant isolation with Testcontainers.
- Contract tests: API envelopes, OpenAPI compatibility and provider adapters.
- Browser tests: happy path, validation, authorization, empty/loading/error states, responsiveness, accessibility and console errors.
- Tests use deterministic clocks/IDs and never depend on execution order.

## Frontend

- App Router with server components by default; use client components only for interaction or browser APIs.
- Feature folders own UI, validation, hooks, services and tests for one domain.
- Shared primitives use shadcn/ui-compatible composition and semantic tokens.
- API URLs exposed to the browser use `NEXT_PUBLIC_`; secrets never enter the client bundle.
- All route-level experiences define loading, error, empty and permission-denied behavior when relevant.
- Motion uses transform/opacity, is restrained, and respects `prefers-reduced-motion`.
- Minimum body text is 16px on mobile; keyboard focus remains visible; no horizontal overflow at 375px.
