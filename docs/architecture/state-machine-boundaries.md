# State-Machine Boundaries

Each aggregate owns one lifecycle. A transition in one machine may trigger a command or event for another, but it never inserts the other machine's state into its enum.

| Aggregate | States defined by the PRD | Boundary rule |
|---|---|---|
| Client | `PROSPECT`, `ACTIVE`, `INACTIVE`, `ARCHIVED` | Onboarding is project-specific and is not a client state. |
| Project | `DRAFT`, `ONBOARDING`, `READY`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `CANCELLED`, `ARCHIVED` | Payment and contract conditions are never project statuses. |
| Onboarding | `DRAFT`, `INVITED`, `IN_PROGRESS`, `AWAITING_INTERNAL_REVIEW`, `NEEDS_REVISION`, `APPROVED`, `COMPLETED`, `PAUSED`, `EXPIRED`, `CANCELLED` | Readiness may move the instance to internal review; final approval is separate. |
| Step | `LOCKED`, `AVAILABLE`, `IN_PROGRESS`, `SUBMITTED`, `UNDER_REVIEW`, `NEEDS_REVISION`, `COMPLETED`, `SKIPPED`, `FAILED`, `CANCELLED` | Feature handlers request validated transitions through the workflow API. |
| Client invitation | `PENDING`, `ACCEPTED`, `REVOKED` | Expiry is derived from `expires_at`; delivery status is independent and never becomes an onboarding state. |
| Invitation delivery | `PENDING`, `SENT`, `FAILED` | A delivery failure leaves the authoritative invitation usable and resendable. |
| Invoice | `DRAFT`, `SENT`, `VIEWED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `VOID`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED` | Balance/status derives from authoritative transactions and policy. |
| Payment transaction | `INITIATED`, `PENDING`, `AUTHORIZED`, `CAPTURED`, `FAILED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED` | Verified provider webhook is authoritative. |
| Contract | `DRAFT`, `GENERATED`, `SENT`, `VIEWED`, `SIGNED`, `DECLINED`, `EXPIRED`, `VOID`, `CANCELLED` | Sent versions are immutable. |
| Form submission | `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `NEEDS_REVISION`, `RESUBMITTED`, `APPROVED` | Earlier submissions remain traceable. |
| Asset | `REQUESTED`, `UPLOADED`, `SCANNING`, `SUBMITTED`, `UNDER_REVIEW`, `NEEDS_REVISION`, `REPLACED`, `APPROVED`, `QUARANTINED`, `REJECTED` | A replacement creates a new version; unsafe files are unavailable. |
| Platform access | `NOT_STARTED`, `REQUESTED`, `CLIENT_SUBMITTED`, `UNDER_VERIFICATION`, `NEEDS_REVISION`, `VERIFIED`, `WAIVED` | Clients may submit; only permitted internal users verify. |
| Task | `TODO`, `IN_PROGRESS`, `BLOCKED`, `IN_REVIEW`, `COMPLETED`, `CANCELLED` | Tasks do not replace onboarding-step state. |

## Readiness invariant

```text
applicable = steps whose validated condition evaluates true
blocking   = applicable steps where blocking is true
ready      = every blocking step is COMPLETED
```

Required/optional controls completion expectations; blocking/non-blocking controls readiness. Optional or informational work cannot accidentally block activation unless its snapshot explicitly marks it blocking.

## Template invariant

Publishing or editing a workflow template version never mutates an active onboarding instance. Starting onboarding copies the selected published version into an immutable snapshot and instantiates steps from that snapshot.
