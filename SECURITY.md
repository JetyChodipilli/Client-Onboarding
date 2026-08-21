# Security policy

## Reporting vulnerabilities

Do not open a public issue containing exploit details, credentials, tenant data, invitation links, provider callbacks, signed documents, uploaded files, or bearer tokens. Report vulnerabilities through the repository owner's private security channel and include the affected version/commit, reproduction steps, impact, and any safe proof-of-concept details.

## Security invariants

- Backend authorization is authoritative. Every tenant-owned path must validate identity, organization, permission, and resource relationship.
- Provider callbacks are signature-verified and idempotent before state mutation.
- Invitation/refresh/reset tokens are stored only as hashes or otherwise protected secret material.
- Uploaded files remain private until policy, signature and malware checks succeed.
- Sent legal content and financial/provider evidence are append-oriented or immutable where required.
- Third-party platform passwords, private keys, API secrets and recovery codes must never be requested or stored.
- Production uses explicit secrets, secure cookies, HTTPS-only origins, private storage and enabled malware scanning.

## Production security gate

The Spring `prod` profile activates `ProductionReadinessValidator`. Startup intentionally fails if local/sandbox defaults are detected. A production deployment therefore requires real payment and e-signature adapters, production sender identity, explicit HTTPS origins, enabled private asset/contract storage and enabled malware scanning.

Never weaken this gate to make a deployment green. Resolve the missing production capability instead.
