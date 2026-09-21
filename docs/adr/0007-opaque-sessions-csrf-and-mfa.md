# ADR 0007: Opaque Server-Side Sessions, CSRF and Privileged MFA

- Status: Accepted
- Date: 2026-08-20

## Decision

Use cryptographically random opaque session tokens in an HTTP-only `BOS_SESSION` cookie. Persist only SHA-256 token hashes in PostgreSQL. Sessions are tenant-bound, expire absolutely, time out when idle, rotate on refresh, and are invalidated when the user's credential version changes.

Cookie-authenticated mutations use a `SameSite=Lax` cookie and Spring Security's cookie-to-header CSRF protection. Cross-origin credentials are limited to the configured frontend allowlist.

Require TOTP MFA when a role includes a privileged permission (`USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_READ`, `PAYMENT_OVERRIDE`, or `PROJECT_ACTIVATE`). Encrypt TOTP secrets with AES-256-GCM using an environment-supplied key. Store recovery-code hashes and consume each code once.

## Rationale

Opaque sessions make password-change invalidation, tenant binding, logout and emergency revocation authoritative without maintaining JWT revocation lists. The cookie never exposes user, role or tenant claims. Permission resolution is repeated from current membership data on every authenticated request.

## Consequences

- PostgreSQL is consulted for authenticated requests; measured caching can be added later without changing the external contract.
- Browser clients must obtain a CSRF token before mutations and send credentials explicitly.
- TLS and `SESSION_COOKIE_SECURE=true` are mandatory outside local development.
- Multi-node deployments also need edge/distributed login rate limiting; the application still enforces account lockout and bounded local source throttles.
