# Threat Model Foundation

Threat modelling starts before sensitive implementation.

| Threat | Primary mitigation direction |
|---|---|
| Tenant escape | authenticated tenant context + permission + relationship validation + tenant-scoped query + tests |
| Authorization failure | permission-based backend checks, deny-by-default, negative tests |
| Invitation token theft/replay | high-entropy token, hash at rest, expiration, one-time/revocable lifecycle |
| Session theft | secure token/session strategy, rotation/invalidation, MFA for privileged users |
| Webhook forgery/replay | provider signature verification, event-ID uniqueness, idempotent transaction |
| Payment tampering | webhook-authoritative final state, explicit override permission/reason/audit |
| Contract tampering | immutable sent version, verified callback, signed artifact retention |
| Unsafe upload | presigned upload, MIME/signature checks, malware scanning, quarantine |
| SSRF | strict integration allowlisting and server-side outbound validation |
| Mass assignment | explicit request DTOs and command mapping |
| Injection/XSS | parameterized persistence, output encoding, framework defaults, validation |
| Brute force | rate limits, lockout/backoff, telemetry; Phase 1 |
| Insider misuse | least privilege + append-oriented audit trail |

Each sensitive phase must refine this model and add concrete tests before completion.
