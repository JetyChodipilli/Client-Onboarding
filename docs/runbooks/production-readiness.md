# Production readiness and incident runbook

## Before release

1. Run backend `mvn -B -ntp verify` with Docker available for Testcontainers.
2. Run a clean PostgreSQL migration from V1 through the latest Flyway version and verify Hibernate schema validation.
3. Run frontend `npm ci`, lint, typecheck, unit tests, production build and Playwright on desktop/tablet/mobile.
4. Run dependency review, CodeQL and the repository's normal dependency audit.
5. Start the backend with `SPRING_PROFILES_ACTIVE=prod`; the production readiness gate must pass without bypasses.
6. Confirm `PUBLIC_API_DOCS=false`, `SECURE_COOKIES=true`, explicit HTTPS CORS origins, WAF/TLS and trusted proxy header handling.
7. Confirm private object-storage buckets, malware scanner health and short-lived signed URLs.
8. Confirm real payment/e-signature provider adapters and webhook secrets; sandbox/disabled providers are rejected by the prod profile.
9. Verify email delivery using the production sender domain and inspect bounded retry/dead delivery behavior.
10. Confirm database backup restoration, rollback procedure and alerting before traffic cutover.

## Security incident

- Revoke affected sessions and rotate relevant secrets/provider credentials.
- Preserve append-only audit/provider evidence.
- Identify tenant/resource scope before any data correction.
- If tenant-data exposure is suspected, stop the affected path, capture request/correlation IDs and query audit/activity logs before remediation.
- Never delete provider/audit evidence to conceal a bad transition; append corrective evidence.

## Outbox/notification backlog

- Inspect oldest pending/failed item and retry counters.
- Repair the downstream dependency first.
- Resume bounded workers; do not bulk mark messages successful.
- Confirm domain state was committed independently from delivery state.

## Payment or contract webhook failure

- Verify signature configuration and replay window.
- Check provider event ID uniqueness and stored payload hash.
- Reprocess only through the idempotent handler; do not edit invoice/contract state manually in SQL.

## File-security outage

- Disable new file-dependent actions if malware scanning/storage is unhealthy.
- Keep pending/unscanned/quarantined objects unavailable for normal download.
- Restore scanner/storage and use the bounded retry path.

## Rollback

Application rollback must not roll back already-applied database migrations. Deploy a compatible application version or create a forward Flyway repair migration. Database restore is a separate disaster-recovery operation and must be practiced against non-production snapshots.
