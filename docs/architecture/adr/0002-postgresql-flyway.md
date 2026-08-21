# ADR-0002: PostgreSQL + Flyway

**Status:** Accepted

## Decision

Use PostgreSQL as the source of truth and Flyway for all production schema changes. Hibernate validates schema; it does not create/update it in production.

## Consequence

Schema evolution is reviewable and repeatable from a clean database. Applied migrations are immutable.
