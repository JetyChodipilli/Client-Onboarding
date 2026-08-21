# Architecture Documentation

These documents capture the architecture and conventions implemented through Phase 11 while remaining subordinate to the PRD.

- `module-map.md` — module ownership and application-port boundaries
- `domain-model.md` — conceptual aggregate relationships
- `dependency-rules.md` — allowed dependency direction
- `system-architecture.md` — runtime/deployment shape
- `development-conventions.md` — coding/review workflow
- `state-machines.md` — independent lifecycle boundaries
- `database-conventions.md` — PostgreSQL/Flyway and tenant constraints
- `api-conventions.md` — `/api/v1`, envelopes, validation/concurrency
- `security-conventions.md` — auth/tenant/provider/file/review controls
- `testing-conventions.md` — unit/integration/security/E2E expectations
- `frontend-conventions.md` — frontend organization
- `design-system.md` — reusable interaction/UX direction
- `threat-model-foundation.md`, `risk-register.md`, `adr/` — governance and decisions

Phase 12 reporting/dashboard behavior is intentionally not documented as implemented.
