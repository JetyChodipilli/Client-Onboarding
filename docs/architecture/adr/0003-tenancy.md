# ADR-0003: Shared Database, Tenant-Scoped Rows

**Status:** Accepted

## Decision

Tenant-owned records carry `organization_id`. Every request-driven lookup enforces tenant scope in authorization/application logic and repository/query predicates.

## Consequence

Cross-tenant access tests are mandatory. An `id`-only repository lookup is insufficient for tenant-owned resources.
