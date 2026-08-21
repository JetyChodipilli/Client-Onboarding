# State-Machine Boundaries

These lifecycles remain independent.

## Client
`PROSPECT -> ACTIVE -> INACTIVE -> ARCHIVED`

## Project
Primary: `DRAFT -> ONBOARDING -> READY -> ACTIVE -> COMPLETED`
Additional documented states: `ON_HOLD`, `CANCELLED`, `ARCHIVED`.

`READY -> ACTIVE` is a separate privileged Phase 11 command; readiness alone never activates a project.

## Onboarding
`DRAFT -> INVITED -> IN_PROGRESS -> AWAITING_INTERNAL_REVIEW -> APPROVED -> COMPLETED`

Revision: `AWAITING_INTERNAL_REVIEW -> NEEDS_REVISION -> IN_PROGRESS -> AWAITING_INTERNAL_REVIEW`
Additional: `PAUSED`, `EXPIRED`, `CANCELLED`.

## Onboarding step
`LOCKED -> AVAILABLE -> IN_PROGRESS -> SUBMITTED -> UNDER_REVIEW -> COMPLETED`

Revision: `UNDER_REVIEW -> NEEDS_REVISION -> IN_PROGRESS`
Additional: `SKIPPED`, `FAILED`, `CANCELLED`.

## Form submission
`DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED`, with `NEEDS_REVISION -> DRAFT/RESUBMITTED` as owned by Forms.

## Asset
Business lifecycle: `REQUESTED -> UPLOADED -> SCANNING -> SUBMITTED -> UNDER_REVIEW -> APPROVED`, with revision/replacement and unsafe `QUARANTINED` / `REJECTED` outcomes. File-version scan state is distinct from asset business state.

## Platform access
`NOT_STARTED -> REQUESTED -> CLIENT_SUBMITTED -> UNDER_VERIFICATION -> VERIFIED`, with `NEEDS_REVISION` and controlled `WAIVED`.

Only authorized internal users can produce VERIFIED.

## Invoice / payment
Invoice: `DRAFT -> SENT -> VIEWED -> PARTIALLY_PAID -> PAID`, plus overdue/void/cancel/refund states.
Payment facts/transactions remain an append-oriented financial ledger and do not become project states.

## Contract
`DRAFT -> GENERATED -> SENT -> VIEWED -> SIGNED`, plus `DECLINED`, `EXPIRED`, `VOID`, `CANCELLED`. Sent legal content is immutable.

## Task
`TODO -> IN_PROGRESS -> IN_REVIEW -> COMPLETED` with documented `BLOCKED` and `CANCELLED` transitions.

## Notification delivery
`QUEUED -> PROCESSING -> SENT/DELIVERED`, with bounded `FAILED -> retry` and terminal `DEAD`/`SUPPRESSED` outcomes.

## Final readiness and review

Mathematical readiness stays exactly:

```text
applicable = snapshotted steps whose condition evaluated true
blocking   = applicable where blocking = true
ready      = every blocking step.status == COMPLETED
```

When readiness first becomes true during active onboarding, lifecycle enters `AWAITING_INTERNAL_REVIEW`.

A human final-review revision can reopen a supported **non-blocking** requirement while mathematical readiness remains true. `FinalReviewRevisionGate` therefore prevents re-entry into final review until every step explicitly returned for revision is completed; it does not alter the readiness formula.

Final approval is separate:

```text
AWAITING_INTERNAL_REVIEW
  -- ONBOARDING_APPROVE --> APPROVED -> COMPLETED
  -- controlled project boundary --> Project READY

Project READY
  -- PROJECT_ACTIVATE --> ACTIVE
```
