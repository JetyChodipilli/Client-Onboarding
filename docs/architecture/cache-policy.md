# Cache and Query-Performance Policy

## Decision

PostgreSQL remains the source of truth for workflow, authorization, tenancy, billing, contracts, readiness and audit state. The platform deliberately does **not** add Redis or an application cache simply because caching technology exists in the reference stack.

This follows the PRD rule that Redis is used only where justified and avoids stale authorization, stale readiness, double-processing and cross-tenant cache-key defects in security-critical flows.

## What is optimized today

- Tenant-prefixed composite indexes follow the actual query paths (`organization_id` first for tenant-owned reads).
- Reporting has dedicated query-path indexes in `V13__reporting_query_indexes.sql` without duplicating mutable business state into a second source of truth.
- `V15__query_performance_hardening.sql` adds indexes only for concrete application query paths: notification feed/admin lists, task due-date views, scheduled-reminder administration, tenant-wide onboarding report ordering, cross-tenant overdue scanning, and outbox notification routing/retry recovery.
- `V16__server_side_search_indexes.sql` supports tenant-bounded prefix search for clients, services and projects so large catalogs are not downloaded and filtered in the browser.
- pgjdbc prepared-statement caching plus `reWriteBatchedInserts`, Hibernate batch fetching/JDBC batching, ordered writes and IN-clause padding reduce round trips/plan churn without weakening ACID boundaries.
- Notification/reminder fan-out joins recipient delivery preferences and email in the resolver, eliminating per-recipient preference/email N+1 lookups. Idempotent notification creation uses `INSERT ... ON CONFLICT DO NOTHING` rather than an application-side check-then-insert race.
- All list APIs are page-bounded or aggregate-bounded; bulk `IN (...)` reads are driven by already-bounded aggregate/result sets.
- Hibernate Open Session in View is disabled, which makes query boundaries explicit and helps expose accidental N+1 behavior during tests/review.
- Optimistic locking protects concurrent mutable aggregates; targeted pessimistic locks are used for idempotency/state-machine operations where serialization is required.

## Safe future cache candidates

Introduce a cache only after latency/DB metrics demonstrate a real hotspot. Good candidates are low-volatility, tenant-scoped reference projections such as:

- published service-catalog summaries;
- published workflow-template metadata (never a live onboarding instance);
- static/versioned instruction-guide content;
- short-TTL dashboard/report snapshots where a small delay is explicitly acceptable.

Every cache key must include `organization_id` plus the resource/version identifier. Invalidation must be tied to the committing transaction/outbox event, never a best-effort controller callback.

## Data that must not use a stale shared cache

Do not cache as authoritative state:

- permissions, role membership or client-project grants;
- session validity, security-token use, invitation validity or credential versions;
- invoice balance, payment transaction state or webhook idempotency;
- contract signature state;
- file malware/quarantine state;
- onboarding step state, readiness or final-review gates;
- audit-log writes.

## Redis adoption gate

If Redis is introduced later:

1. record the measured query/latency problem first;
2. define cache key, TTL and invalidation semantics in an ADR;
3. include tenant-isolation tests for cache keys;
4. make Redis optional for correctness unless a future architecture decision explicitly promotes it to a critical dependency;
5. define safe behavior during cache outage and recovery;
6. instrument hit rate, miss rate, evictions, errors and stale-data incidents.

Caching is a performance optimization, not a correctness layer.
