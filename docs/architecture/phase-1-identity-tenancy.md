# Phase 1 Identity, Authentication, RBAC and Tenancy

## Boundary and ownership

Phase 1 implements employee identity and access administration only. It does not create clients,
client-portal users, services, projects, onboarding instances, workflows, invoices or contracts.

| Module | Phase 1 responsibility |
|---|---|
| `identity` | Global user account, credential state, verification state and lock state |
| `organization` | Tenant, membership, role, permission assignment and last-manager policy |
| `auth` | Opaque sessions, recovery tokens, invitation tokens, TOTP, recovery codes and security mail adapter |
| `audit` | Append-oriented, tenant-scoped security and administration evidence |
| `common` | Principal, request metadata, API envelopes, error mapping and observability primitives |

ArchUnit prevents `common` from depending on feature modules, prevents domain code from depending
on API/infrastructure code, and rejects top-level dependency cycles. Cross-module work is performed
through application/domain ports; controllers do not access persistence.

## Identity and tenant model

- A user is a global credential-bearing identity with an `INTERNAL` or `CLIENT` principal type.
- An `organization_users` row grants that user access to exactly one organization and one role.
- Roles belong to one organization. Role assignment is protected by a composite
  `(organization_id, role_id)` foreign key.
- Permission codes are stable catalog identifiers. Role names carry no authority.
- Internal authentication only accepts active internal identities, active organizations and active
  memberships.
- Client identities remain a schema/security boundary only; client activation and portal access are
  intentionally deferred to Phase 4.

## Authentication sequence

1. A browser initializes CSRF and submits email, organization slug and password.
2. The backend applies source/subject throttling, performs a constant-work password check and returns
   the same invalid-credential response for unknown tenant, identity or password.
3. Privileged permissions require TOTP. Enrollment secrets are AES-256-GCM encrypted; recovery codes
   are hashed and displayed only on enrollment.
4. Successful authentication creates a random opaque session value. Only its SHA-256 hash is stored.
5. The HTTP-only, SameSite `Lax` cookie is validated against absolute expiry, idle expiry, revocation,
   identity credential version, membership state and organization state on every request.
6. Refresh rotates the opaque token. Password changes revoke all sessions and increment credential
   version.

Reset, verification, invitation and MFA challenge values are random, hashed at rest, expiring and
single use. Consuming a token and applying the protected change occur in one transaction.

## Authorization and isolation rules

- Spring method security is authoritative; UI permission checks only hide unusable navigation.
- Every tenant-owned lookup uses the authenticated organization identifier. Resource identifiers from
  the URL never select a row alone.
- A cross-tenant object returns the same `RESOURCE_NOT_FOUND` response as a nonexistent object.
- Organization and role mutations use optimistic versions.
- A membership cannot assign a role from another tenant because both application lookup and database
  constraints enforce organization ownership.
- The last active membership carrying both `USER_MANAGE` and `ROLE_MANAGE` cannot be demoted,
  suspended or archived, and its role cannot lose those permissions.
- Settings lists are bounded to 100 records in Phase 1; audit logs use page/size with a maximum of 100.

## Audit and privacy

Security and administration changes append an audit row in the same transaction as the protected
change. Evidence includes organization, actor, action, entity, timestamp, request/correlation IDs,
source and a SHA-256 address fingerprint. Passwords, raw session tokens, raw security links, TOTP
secrets and recovery codes are never logged or stored in audit state.

Application code uses the direct socket address for rate-limit and audit fingerprints. Deployments
must normalize client addresses at a trusted proxy/container boundary and must not pass untrusted
forwarding headers through as authoritative values.

## Operational configuration

Required production settings include a base64-encoded 32-byte `MFA_ENCRYPTION_KEY`, secure cookies,
TLS, an exact CORS allowlist, SMTP transport, database credentials and a public application URL.
Invalid or missing MFA key material prevents startup. Bootstrap provisioning is disabled by default,
requires explicit environment values, is slug-idempotent and should be disabled after first use.

The in-process sign-in limiter is deliberately a defense-in-depth control. Multi-instance deployments
must also enforce distributed throttling at the trusted ingress; Redis is not introduced in Phase 1.
