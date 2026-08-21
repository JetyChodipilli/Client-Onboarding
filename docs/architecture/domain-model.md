# Conceptual Domain Model Through Phase 11

```mermaid
erDiagram
    ORGANIZATION ||--o{ ORGANIZATION_USER : has
    ORGANIZATION ||--o{ CLIENT : owns
    CLIENT ||--o{ CLIENT_CONTACT : has
    CLIENT ||--o{ CLIENT_USER : authorizes
    CLIENT ||--o{ PROJECT : has
    SERVICE ||--o{ PROJECT : classifies
    PROJECT ||--o{ PROJECT_MEMBER : has
    PROJECT ||--|| ONBOARDING_INSTANCE : onboards
    WORKFLOW_TEMPLATE ||--o{ WORKFLOW_TEMPLATE_VERSION : versions
    WORKFLOW_TEMPLATE_VERSION ||--o{ WORKFLOW_TEMPLATE_STEP : defines
    WORKFLOW_TEMPLATE_VERSION ||--o{ ONBOARDING_INSTANCE : snapshots
    ONBOARDING_INSTANCE ||--o{ ONBOARDING_STEP_INSTANCE : contains
    ONBOARDING_INSTANCE ||--o{ ONBOARDING_REVIEW : final_review_evidence
    ONBOARDING_STEP_INSTANCE ||--o{ TASK : may_generate
    ONBOARDING_STEP_INSTANCE ||--o{ FORM_SUBMISSION : may_require
    ONBOARDING_STEP_INSTANCE ||--o{ ASSET : may_require
    ONBOARDING_STEP_INSTANCE ||--o{ PLATFORM_ACCESS_REQUEST : may_require
    PROJECT ||--o{ INVOICE : bills
    INVOICE ||--o{ PAYMENT_TRANSACTION : ledger
    PROJECT ||--o{ CONTRACT : contracts
    ORGANIZATION ||--o{ NOTIFICATION : delivers
    REMINDER_POLICY ||--o{ SCHEDULED_REMINDER : schedules
```

## Important ownership rules

- Client, contact and client-auth identity remain separate.
- One project belongs to one tenant/client/service while a client may own many projects.
- Onboarding belongs to a project; it is not a client status.
- Workflow instances snapshot a published definition so later edits cannot mutate an active onboarding.
- Forms, assets, platform access, billing and contracts own their records; workflow only owns requirement state/snapshot orchestration.
- Payment, contract, project, onboarding and step states are not aliases for one another.
- Review decisions are append-only evidence.
- Final approval completes onboarding and advances project to READY; activation is separately authorized.
