# Phase 3: Workflow engine

## Template model

`WorkflowTemplate` is a tenant-owned, optionally service-scoped container. Each `TemplateVersion` has a monotonic
number and is either `DRAFT`, `PUBLISHED`, or `ARCHIVED`. Draft replacement and publication use optimistic
locking. Published steps are immutable; a new draft may copy a published version with remapped dependency IDs.

Each step records type, order, required/blocking flags, client visibility, review policy, assignment, relative
due time, reminder-policy reference, skip/reopen policy, feature configuration, condition, and dependencies.
The engine validates edge membership, mode cardinality, uniqueness, and acyclicity before persistence.

## Instance model

Starting onboarding requires `ONBOARDING_START`, an active project-visible template, a published version, and a
matching service scope. The transaction writes:

1. An immutable JSON snapshot of the template version.
2. Materialized step instances with evaluated applicability and calculated due times.
3. Same-instance dependency edges.
4. The project transition from `DRAFT` to `ONBOARDING`.
5. An audit record and, when supplied, an idempotency record.

Conditions use a non-executable DSL over `SERVICE_CODE`, `PROJECT_VALUE_MINOR`, `CLIENT_STATUS`, and
`CURRENCY_CODE`. The `ALL` and `ANY` dependency resolver unlocks eligible steps after completion. Review-required
transitions require `ONBOARDING_REVIEW`; normal progression requires `ONBOARDING_START`.

## Readiness

```text
applicable = condition evaluates true
blocking   = applicable and blocking
ready      = every blocking step is COMPLETED
```

Skipped blocking steps do not count as ready. Published templates and existing snapshots are unaffected by
subsequent drafts. Phase 3 leaves onboarding lifecycle at `DRAFT`; invitations/client participation are Phase 4,
and final approval/project activation are Phase 11.
