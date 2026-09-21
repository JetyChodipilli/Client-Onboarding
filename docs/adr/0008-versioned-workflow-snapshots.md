# ADR 0008: Versioned workflow snapshots and safe conditions

Status: Accepted

## Context

Workflow templates evolve while existing client onboarding must remain historically accurate. Steps need
ordering, dependencies, applicability, review rules, due dates, assignments, skip/reopen policy, and feature
configuration without executing tenant-authored code.

## Decision

- A template is a tenant-owned container with numbered child versions.
- Only `DRAFT` versions may be edited. Publishing is an optimistic, irreversible transition.
- Starting onboarding requires a published version and writes both an immutable JSON snapshot and normalized
  step instances in one transaction.
- Dependencies use explicit `NONE`, `ALL`, or `ANY` modes. The domain rejects dangling, duplicate, self, and
  cyclic edges; database composite foreign keys enforce same-version and same-instance membership.
- Conditions use the fixed `field`, `operator`, `value` model. Supported fields come from the project/client
  snapshot context; no expression or script engine is present.
- Readiness derives only from applicable blocking steps. Optionality and blocking remain independent.
- Onboarding-start retries use a tenant/scope/key record, request fingerprint, and transaction-scoped tenant
  row lock. Reusing a key for a different command is a conflict.

## Consequences

Existing onboarding does not change when templates are edited later. SQL storage is explicit and queryable,
while JSON preserves the exact historical definition. New condition fields and step handlers require reviewed
code rather than tenant-authored execution. Phase 4 will add invitations and client-facing progression; Phase 11
will add final approval and project activation.
