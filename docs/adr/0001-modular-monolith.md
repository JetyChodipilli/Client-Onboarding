# ADR 0001: Modular Monolith for MVP

- Status: Accepted
- Date: 2026-08-20

## Decision

Build one Spring Boot deployable with explicit domain modules and one PostgreSQL database. Enforce module boundaries through package ownership, architecture tests and application ports.

## Consequences

Transactions and local development remain simple, while module contracts keep later extraction possible. Direct cross-module table mutation and cyclic domain dependencies are prohibited.

