# ADR-0004: Transactional Outbox Before Kafka

**Status:** Accepted

## Decision

Use Spring application/domain events plus a transactional outbox and background workers for reliable asynchronous work in the MVP. Kafka is deferred until throughput, replay, independent consumers, or service decomposition justify it.
