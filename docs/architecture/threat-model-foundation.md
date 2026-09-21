# Threat-Model Foundation

| Threat | Current control through Phase 3 | Required later proof |
|---|---|---|
| Tenant escape / BOLA | Tenant-scoped identity, client, service, project, workflow, onboarding and audit repositories; cross-tenant reads use secure 404 | Repeat cross-tenant read/update tests for each later resource |
| Privilege escalation | Method-level permission checks, scoped roles, MFA session assurance, last-manager policy, and separate workflow/read/review authorities | Expand the deny/allow matrix with every permission |
| Token theft/replay | Hashed opaque tokens, expiry, single use, session rotation and credential-version invalidation | Add provider/invitation replay scenarios in later phases |
| Brute force | Atomic account lockout plus bounded source/subject limiter and generic errors | Add distributed edge rate limiting at deployment |
| SQL injection | Parameterized JDBC queries, bounded search/page values, and enum-controlled transitions | API mutation/fuzz tests |
| XSS | React escaping and strict DTO/rendering conventions | Stored/reflected XSS tests and CSP review |
| CSRF | SameSite session cookie plus cookie/header CSRF token validation | Revalidate behind the production proxy and TLS termination |
| SSRF | External destinations must be configured allowlists | Redirect, DNS rebinding and private-network tests |
| Unsafe uploads | Quarantine/scanning architecture recorded | MIME/signature, size, malware and download authorization tests |
| Webhook forgery | Signature + event-id + idempotency invariant | Invalid, duplicate, delayed and reordered callback tests |
| Mass assignment | Explicit request DTOs/application commands; no persistence entity binding | Continue forbidden-field API tests |
| Workflow expression execution | Fixed field/operator/value DSL; graph validation; no script engine | Fuzz condition payloads and later feature-handler inputs |
| Concurrent mutation / replay | Optimistic versions, unique constraints, transaction-scoped tenant lock, request fingerprint | Add external-provider idempotency proofs in their phases |
| Secret leakage | Environment-only configuration, fail-fast MFA key validation and redaction rules | Secret scanning and log review |

Rate-limit and audit address fingerprints use the remote socket address and do not trust caller-supplied
forwarding headers. A production ingress must strip untrusted forwarding headers and normalize the
client address at a trusted proxy/container boundary.

See ADR 0007 for the selected opaque-session, CSRF, MFA and credential-invalidation model.
