# ADR 0002: PostgreSQL and Flyway as Schema Authority

- Status: Accepted
- Date: 2026-08-20

## Decision

PostgreSQL is the source of truth. Flyway manages forward-only, versioned migrations. Hibernate schema generation is disabled; validation is permitted after entities exist.

## Consequences

Every schema change is reviewable and reproducible. Clean-database migration is a release gate. Applied production migrations are never rewritten.

