# Phases 0–4 debug audit

Scope: implemented Phases 0–3 on `main` plus Phase 4 on `codex/phase-4-client-portal`.
No new phase, provider integration or deployment. Requested workflows: Ponytail Audit, Ponytail Review
and gstack investigation/review/shipping.

## Findings and fixes

| Phase | Confirmed defect | Fix and regression coverage |
|---|---|---|
| 0 | Malformed UUID/query parameters and missing required query parameters reached the generic 500 handler | Controlled 400 envelope; PostgreSQL-backed API regressions |
| 1 | Concurrent or already-authenticated revoked sessions could rotate more than once | Atomic session consumption scoped by organization/user, expiry and credential version; replay and revocation regression |
| 1 | Concurrent removal of manager permissions could leave no manager | Organization row lock before role/member changes; two simultaneous role edits yield one success and one last-manager conflict |
| 1 | Audit controller accessed persistence directly | Existing audit service now owns the query; new ArchUnit controller/persistence boundary rule |
| 1–3 | Page multiplication could overflow into negative SQL offsets | Long offsets in audit, client, service, project and workflow lists; maximum integer page regression |
| 3–4 | Step mutations ignored project hold/cancel and internal transitions ignored paused/closed onboarding | Shared project application port with transactional row lock; hold/resume/cancel/archive, paused/terminal and concurrent hold regressions |
| 4 | Invitations remained usable while a project was held | ONBOARDING project filter plus mutation lock; create/resend/accept rejection tests |
| 4 | Locked steps awaiting prerequisite review were assigned back to the client | Review waits belong to the team; ALL/ANY dependency, revision and hidden-prerequisite tests |
| 1, 4 | Logout network failures were swallowed before navigating away | Shared sign-out hook retains visible error and retry; tests for both shells plus responsive browser scenario |
| Docs | Phase 4 report incorrectly described later-phase work on main | Corrected scope against fetched Git history; retained main's architecture README in conflict resolution |

## Ponytail audit and review

- `[shrink]` Both shells now reuse the same sign-out behavior instead of copying failure/retry logic.
- `[native]` PostgreSQL conditional updates and row locks enforce concurrency; no new coordination library.
- No safe speculative feature or security/test deletion was identified. Estimated additional cuts: 0 lines.
- Existing module ports, bounded repositories and migration history remain. No package dependencies added.
- Required regression code increases the diff; test volume is not treated as unnecessary complexity.

## Validation

Local frontend lint, strict typecheck, 12 unit tests and production build passed. Playwright discovers
92 scenarios (four viewports; the existing full-stack bootstrap case intentionally runs only once).
Backend, clean PostgreSQL migrations, browser execution and container startup are being verified in GitHub
Actions. This report is not a claim that pending checks passed. No applied migration was changed.

## Merge handling

Main at review start: `e3cb98f6d0c374682a35918b0e762a080baa4696`. Phase 4 baseline:
`2aba9c0948c87a2ff8033231ff66daf9949df886`. The README conflict is resolved by keeping main's
architecture/tradeoff narrative and adding accurate Phase 4 scope and verification links.
Merge is gated on current backend, frontend, browser and container checks; no force push.

## Limits

This is a code, security-boundary and regression audit of implemented scope, not a proof of zero defects
or completion of Phase 13 penetration/load/production hardening. Per-instance login throttling remains
a documented deployment limitation. Production email/ingress secrets and later phases are unchanged.
