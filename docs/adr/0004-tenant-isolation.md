# ADR 0004: Shared Database, Shared Schema, Tenant-Keyed Rows

- Status: Accepted
- Date: 2026-08-20

## Decision

Use a shared PostgreSQL schema with `organization_id` on every tenant-owned row. Enforce isolation at authorization, application and repository/query layers.

## Consequences

Every tenant-resource lookup includes organization context. Cross-tenant read/update tests are mandatory. Elevated platform support access requires a separate controlled and audited design.

