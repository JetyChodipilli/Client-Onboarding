# ADR 0003: Transactional Outbox Before Kafka

- Status: Accepted
- Date: 2026-08-20

## Decision

Record business changes and corresponding outbox events in one PostgreSQL transaction. Background workers publish or deliver after commit. Kafka is not an MVP dependency.

## Consequences

Critical events cannot be silently lost between database commit and external publication. Workers must be idempotent, observable, bounded in retries and recoverable.

