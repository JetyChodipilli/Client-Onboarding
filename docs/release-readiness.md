# Release readiness checklist

This checklist is a gate, not a documentation-only exercise.

- [ ] PRD and architecture decisions reviewed
- [ ] Threat model reviewed for tenant escape, IDOR/BOLA, token theft, unsafe upload, payment/contract tampering, webhook forgery and insider misuse
- [ ] Flyway V1 → latest clean migration passes on PostgreSQL
- [ ] Hibernate schema validation passes
- [ ] Backend unit/integration/security tests pass
- [ ] Tenant-isolation negative tests pass
- [ ] Payment and contract webhook verification/idempotency tests pass
- [ ] File-security tests pass
- [ ] Frontend lockfile is generated/committed and reproducible install (`npm ci`) passes
- [ ] Frontend lint/typecheck/unit/build pass
- [ ] Representative task/notification/report/worker queries pass `EXPLAIN (ANALYZE, BUFFERS)` review on production-like data
- [ ] Hikari/PostgreSQL pool sizing and P95 API/dashboard latency meet the PRD baseline under load
- [ ] Any enabled shared cache has a tenant-safe key, TTL, committed-event invalidation, outage behavior and an approved ADR
- [ ] Mocked-API Playwright UI regression passes on desktop/tablet/mobile
- [ ] Non-mocked full-stack Playwright/UAT passes against Next.js + Spring Boot + PostgreSQL
- [ ] CodeQL/dependency review pass with no unresolved high/critical findings
- [ ] Production profile startup validation passes
- [ ] Gateway/WAF API abuse throttling policy is configured and verified
- [ ] Object storage is private; malware scanning enabled
- [ ] Real payment/e-signature production adapters configured
- [ ] Monitoring/alerts and `/health/live` + `/health/ready` verified
- [ ] Backup restoration tested
- [ ] Rollback/runbooks exercised
- [ ] UAT and production smoke checklist approved

A release is **not production ready** while any mandatory item above is unverified.
