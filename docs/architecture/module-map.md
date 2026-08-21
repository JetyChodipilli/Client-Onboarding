# Domain / Module Map

## Implemented ownership through Phase 11

| Module | Primary ownership |
|---|---|
| `auth` | internal/client authentication, sessions, credentials, MFA/security tokens |
| `identity` | global user identities |
| `organization` | organizations, memberships, RBAC, employee invitations |
| `client` | client companies, contacts, client-user relationships |
| `servicecatalog` | tenant service catalog |
| `project` | project aggregate, team, project lifecycle and READY/ACTIVE transitions |
| `workflow` | versioned workflow templates, validated conditions/dependencies, step definitions |
| `onboarding` | immutable workflow snapshots, onboarding/step lifecycle, readiness and final review |
| `forms` | form/version/field/submission/review lifecycle |
| `assets` | asset requirements, secure versions, scan/review lifecycle |
| `access` | versioned platform-access guides, client submission/internal verification |
| `billing` | invoice aggregate/items/policies and reconciliation projection |
| `payments` | provider sessions, payment ledger/refunds/webhook processing |
| `contracts` | legal templates/versions, contracts, recipients, signatures and signed-document evidence |
| `tasks` | manual/system/workflow tasks and assignment lifecycle |
| `notifications` | templates, in-app notifications, preferences and delivery records/workers |
| `reminders` | reminder policies, schedules, responsibility routing and suppression |
| `integrations` | shared provider/webhook/event-processing boundaries |
| `audit` | append-oriented privileged/state-changing audit evidence |
| `common` | API, observability, security principals, outbox/activity technical primitives |
| `reporting` | **reserved for Phase 12; not implemented** |

## Dependency rule

```text
api -> application -> domain
             |
             +-> own infrastructure/persistence

module.application -> sibling.application public port only
```

A module must not import another module's persistence repository or write another module's table directly. Cross-module orchestration uses public application services/ports and, for asynchronous effects, the transactional outbox.

Examples:
- `project` resolves clients/services/members through public lookup services.
- workflow step initialization/transition extensions allow Forms/Assets/Access/Tasks to own their domain records without workflow table mutation.
- final-review revision handlers let owning feature modules reopen supported records while Onboarding owns review lifecycle/evidence.
- Onboarding calls the controlled Project activation boundary to mark a project READY after successful approval.

## Data ownership by migration

- V1 foundation.
- V2 identity/auth/RBAC/audit.
- V3 clients/services/projects/activity.
- V4 workflow/onboarding/outbox.
- V5 client invitations/auth/project grants.
- V6 forms.
- V7 assets.
- V8 billing/payments/webhook ledger.
- V9 contracts/e-signature evidence.
- V10 platform access.
- V11 tasks/notifications/reminders.
- V12 final-review evidence and project readiness/activation guards.

## Event boundary

Reliable asynchronous side effects use PostgreSQL transactional outbox + bounded workers. Kafka remains deferred until an explicit throughput/decomposition requirement justifies it.

## Reporting read model (Phase 12)

`reporting` is a read-only cross-module projection. It may query PostgreSQL source-of-truth tables through tenant-scoped SQL for dashboards and analytics, but it owns no business lifecycle and may not mutate another module's tables. `REPORT_READ` is the server-authoritative permission boundary.
