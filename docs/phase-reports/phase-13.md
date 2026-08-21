# Phase 13 — Security Hardening, E2E Testing & Production Readiness

## Source hardening

- Production-only startup validation rejects local/sandbox security defaults.
- API defense-in-depth security headers and no-store behavior for protected/health responses.
- Frontend HSTS, COOP, anti-framing, MIME, referrer, permissions and crawler headers.
- Backend/frontend Compose containers run with dropped Linux capabilities, no-new-privileges, read-only root filesystem and bounded `/tmp` tmpfs.
- CodeQL and dependency-review workflows added.
- Production security policy, release checklist and operational runbook added.
- Phase 13 Playwright source checks response headers, anonymous workspace protection and mobile keyboard/overflow behavior.

## Intentional fail-closed production gate

`SPRING_PROFILES_ACTIVE=prod` rejects disabled/sandbox payment or e-signature providers. This is deliberate: provider abstractions/sandboxes are suitable for development and integration testing, but a production release requires configured real adapters. The gate must not be weakened merely to deploy.

## Release-audit corrections

- Production startup now verifies that the configured payment and e-signature provider names resolve to an actually available adapter in this build. A made-up non-sandbox provider name can no longer bypass the fail-closed production gate.
- Corrected a Phase 6 Java record factory/accessor collision in `MalwareScanner.ScanResult` that would have prevented full Java compilation.
- Closed high-impact Phase 6–8 frontend route gaps for internal asset/billing/contracts operations and client PAYMENT/CONTRACT next actions.

## Remaining executable evidence

This runtime has no Maven, Docker or PostgreSQL tooling and cannot resolve npm dependencies. Consequently the clean V1→V14 migration, Maven verify, Spring production startup, Next build, Vitest and Playwright runs cannot be truthfully marked PASS here.

## Cross-phase defects fixed during final hardening

- Fixed invalid single-line Java text-block delimiters in Phase 10 notification/reminder workers that would have blocked Java compilation.
- Revalidated the Phase 11 database `READY` transition forward-only in V14.
- Defined and ArchUnit-enforced the narrow Billing/Payments shared financial consistency boundary in ADR 0007; unrelated modules remain prohibited from financial persistence.
- Added Phase 12 reporting input-validation unit-test source and reran grammar/static validation.
