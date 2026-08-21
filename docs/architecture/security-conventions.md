# Security Conventions

## Protected-request sequence

```text
Authenticated identity/session?
  -> correct active tenant scope?
  -> required permission?
  -> tenant-scoped resource lookup?
  -> valid project/client/domain relationship?
```

Frontend permission checks are UX only.

## Authentication/session baseline

- short-lived bearer access tokens;
- opaque refresh tokens hashed at rest and rotated; reuse invalidates the affected session family;
- HttpOnly/SameSite refresh cookie with trusted-origin/fetch-metadata protection for ambient-cookie commands;
- hashed, expiring, single-use verification/reset/invitation challenges;
- MFA for privileged permission sets;
- bounded login/rate-limit controls and credential-change invalidation.

Internal users and client users remain separate security principals/scopes.

## Tenant/resource isolation

Defense in depth: authenticated tenant principal, permission annotation, application relationship validation, organization-scoped repository query, and composite database constraints/triggers for security-critical relationships. Request-driven tenant aggregates are not loaded by global ID alone.

## Provider/file/legal/financial protection

- provider callbacks verify signatures/timestamps and idempotent provider event IDs before mutation;
- secrets belong in deployment secret mechanisms, not domain columns/source;
- files remain private and unavailable until size/MIME/signature/malware policy succeeds;
- signed legal documents are privately stored with retained evidence/hash and short-lived authorized download URLs;
- payment transaction and review histories are append-oriented rather than rewritten;
- platform access never asks for third-party passwords/tokens.

## Phase 10 worker protection

Notification routing/delivery and reminder workers use bounded retries, processing leases and terminal inspectable failure states. Preference/suppression rules affect actual delivery/surface visibility. Completed/cancelled/paused/invalid reminder targets are suppressed.

## Phase 11 review/activation protection

- readiness is recomputed server-side from snapshotted blocking steps;
- a final-review revision gate prevents a still-outstanding non-blocking revision from bouncing straight back to approval;
- selected revision requirements must have an owning revision handler; payment and signed-contract facts are not rewritten by final review;
- review evidence is append-only and database-validated against reviewer, onboarding version and lifecycle;
- approval requires `ONBOARDING_APPROVE` and moves the project to READY only after completed onboarding;
- activation requires `PROJECT_ACTIVATE`, an exact expected project version and current READY state;
- database triggers prevent direct SQL from bypassing READY/ACTIVE lifecycle evidence.

## Production profile gate (Phase 13)

Production starts with `SPRING_PROFILES_ACTIVE=prod`. `ProductionReadinessValidator` intentionally rejects local/sandbox origins, public API docs, insecure cookies, bootstrap mode, disabled private storage/scanning and sandbox/disabled payment or contract providers. The correct remediation is production configuration/provider implementation, never weakening the gate.
