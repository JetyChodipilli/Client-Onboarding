# ADR 0007 — Billing and Payments Share One Financial Consistency Boundary

## Status
Accepted.

## Context
The PRD requires provider-confirmed payment facts, invoice balances/status, workflow synchronization and the transactional outbox to remain consistent. Billing and payment code are separated by package and API responsibility, but a verified payment transaction and its invoice reconciliation must commit atomically in PostgreSQL.

## Decision
Treat `billing` and `payments` as two application submodules inside one **financial consistency boundary**. They may use each other’s persistence repositories only for the atomic ledger/reconciliation transaction. All other modules are prohibited from importing either financial persistence package; ArchUnit enforces this. External/provider I/O remains outside the database transaction.

This is a narrow exception to the default rule that sibling modules interact only through application interfaces. It does not permit controllers or unrelated modules to access financial repositories.

## Consequences
- Payment ledger + invoice reconciliation stays ACID without distributed transactions.
- Financial persistence cannot leak into onboarding, workflow, reporting, contracts or other modules.
- If billing/payments are split into independent deployables later, this boundary must be replaced with an explicit durable integration protocol and reconciliation strategy.
