# Module Dependency Rules

1. `api` may depend on its module's `application` and API DTOs, not persistence implementations.
2. `application` may depend on `domain` abstractions and explicit public interfaces from other modules.
3. `domain` must not depend on Spring MVC, JPA repositories from other modules, or external provider SDKs.
4. `infrastructure` implements domain/application ports and may depend on frameworks/provider SDKs.
5. `common` contains technical primitives only; it must not become a dumping ground for domain logic.
6. Cross-module database writes are prohibited except inside the explicitly documented Billing/Payments financial consistency boundary (ADR 0007), where ledger append + invoice reconciliation must be atomic.
7. Reporting can read purpose-built projections/read models but must not become a bypass around tenant authorization.
8. Cycles between domain modules are architecture defects and must be refactored into clear ownership or orchestration.

## High-level dependency direction

```text
API -> Application -> Domain
                  ^
                  |
          Infrastructure
```

Cross-module calls are explicit at the application boundary.
