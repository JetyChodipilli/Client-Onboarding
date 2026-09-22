# Domain Module Map

## Ownership and dependencies

| Module | Owns | Allowed direct dependencies |
|---|---|---|
| `common` | API envelopes, errors, IDs, time, observability primitives | None |
| `identity` | User identity and account profile | `common` |
| `organization` | Tenants, memberships, roles, permissions | `common`, `identity`, `audit`; auth implements its security port |
| `auth` | Authentication, sessions/tokens, verification, reset, MFA | `common`, `identity`, `organization`, `audit` |
| `client` | Client companies and contacts | `common`, `audit` |
| `servicecatalog` | Organization service definitions | `common`, `audit` |
| `project` | Projects, members, lifecycle and activity timeline | `common`, `audit`, `organization`, `client`, `servicecatalog` |
| `workflow` | Templates, versions, steps, conditions and dependencies | `common`, `audit`, `servicecatalog` |
| `onboarding` | Workflow snapshots, instances, step instances and readiness | `common`, `audit`, `project`, `workflow` |
| `portal` | Client invitations, client-user linkage, project grants and portal read model | `common`, `audit`, `auth`, `identity`, `onboarding`, `project`, `workflow` |
| `forms` | Form templates/versions, submissions, answers and review | `common`, `organization`, `project`, `workflow` SPI |
| `assets` | Requirements, metadata, versions, scan/review state and download authorization | `common`, `organization`, `project`, `workflow` SPI |
| `access` | Access types, guides, requests and verification | `common`, `organization`, `project`, `workflow` SPI |
| `billing` | Invoices, items, policies, balances and refunds | `common`, `organization`, `project`, `workflow` SPI |
| `payments` | Payment transactions, provider ports and verified webhooks | `common`, `billing`, `integrations` |
| `contracts` | Templates, immutable versions, recipients, signatures and callbacks | `common`, `organization`, `project`, `workflow` SPI, `integrations` |
| `tasks` | Manual/system/workflow tasks and assignments | `common`, `organization`, `project` |
| `notifications` | Templates, notifications, preferences and delivery attempts | `common`, `organization` |
| `reminders` | Policies, schedules, suppression and retries | `common`, `notifications`, read-only onboarding port |
| `integrations` | Connection metadata and provider adapter contracts | `common`, `organization` |
| `reporting` | Read models, metrics, filters and exports | `common`, read-only query ports |
| `audit` | Append-oriented audit and activity records | `common`, consumes domain/outbox events |

## Dependency rules

- `common` cannot depend on a business module.
- Domain packages cannot depend on controllers, JPA adapters or provider SDKs.
- Feature modules implement the workflow handler SPI; the workflow engine does not branch on feature types through repeated `if/else` chains.
- The onboarding module evaluates snapshot/step state, not invoice/contract tables directly.
- Reporting uses query ports or dedicated read models and never mutates source modules.
- Audit consumes immutable event facts and is not called as an editable business repository.
- Cross-module database writes are prohibited.
- Top-level module slices must remain cycle-free; ArchUnit enforces this on every build.
- Synchronous security audit appends are an explicit dependency for identity-changing transactions.

## Backend package shape

Significant modules use only the layers they need:

```text
module/
├── api/               HTTP requests, responses and controllers
├── application/       use cases, commands, queries and transactions
├── domain/            model, policies, events and repository ports
└── infrastructure/    persistence, integration and configuration adapters
```

Tiny foundation modules may use fewer folders; empty layers are not created for appearance.
