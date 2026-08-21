# Phase 0 Risk Register

| Risk | Impact | Foundation response | Owning phase / gate |
|---|---|---|---|
| Tenant escape through id-only lookup | Critical data exposure | shared-DB tenant convention, deny-by-default security, tenant-scoped repository rule, mandatory negative tests | Phase 1 onward |
| Role-name checks bypass permission model | Privilege escalation | permission-based RBAC convention; role names are not authorization rules | Phase 1 |
| Template edits mutate active onboarding | Workflow/legal inconsistency | version/snapshot boundary documented | Phase 3 |
| External event replay/forgery | Duplicate or fraudulent state changes | provider adapter + signature + event-id/idempotency architecture | Phases 7–8 |
| Unsafe uploads | Malware/data exposure | presigned, scan, MIME/signature, quarantine architecture | Phase 6 |
| Cross-module table mutation | Hidden coupling and broken invariants | module ownership and ArchUnit foundation | Every phase |
| Lost asynchronous side effects | Inconsistent business state | transactional outbox ADR; implement with first reliable async use case | Relevant event-producing phase |
| Schema drift | Startup/runtime failure | Flyway-only migrations + Hibernate validate + clean-DB integration test | Every schema phase |
| Supply-chain vulnerability | Remote compromise | version review, Dependabot, CI dependency installation/build; security patch gate before release | Every phase / Phase 13 |
| Unverified generated foundation | False production confidence | phase cannot be marked DoD complete until build, migrations, startup, and browser tests execute in a capable environment | Phase 0 exit gate |
