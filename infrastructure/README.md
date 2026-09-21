# Infrastructure Foundation

## Local topology

Docker Compose runs PostgreSQL, the Spring Boot API and the Next.js frontend. Redis, object storage emulators and message brokers are not started in Phase 0 because no implemented access pattern requires them.

## Production expectations

- Managed PostgreSQL with encrypted connections, backups, point-in-time recovery and tested restore.
- CDN/WAF/TLS in front of the frontend and API/load balancer.
- Immutable application images run as non-root with read-only filesystems where practical.
- Secrets injected by the deployment platform; never baked into images or ordinary configuration repositories.
- Rolling or blue/green deployment with backward-compatible migrations.
- Separate development, QA/test, staging and production accounts/databases/secrets.
- Metrics, error monitoring, structured logs and alerts for 5xx rate, latency, database saturation and worker/outbox failures.

Production manifests, backup automation, RPO/RTO, alert thresholds and rollback runbooks belong to Phase 13 after the target cloud/runtime is approved.

