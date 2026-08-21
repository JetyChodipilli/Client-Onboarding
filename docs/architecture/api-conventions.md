# API Conventions

Business APIs use `/api/v1` and the standard success/error envelopes with `requestId`.

## Security and validation

- Backend authorization is authoritative.
- Protected tenant commands validate authentication, tenant, permission and resource relationship.
- Explicit request DTOs prevent mass assignment.
- Validation errors use stable domain codes/field errors; no stack traces/SQL/provider secrets leak to callers.
- Cross-tenant identifiers use secure not-found/authorization semantics.

## Pagination

Collections use zero-based `page`, bounded `size` (server maximum currently 100) and deterministic sorting. Page metadata belongs in `meta`.

## Idempotency / concurrency

Use `Idempotency-Key` for duplicate-sensitive commands required by the PRD. Mutable aggregate commands carry expected versions where needed and return controlled HTTP 409 conflicts on stale state rather than silently overwriting.

## Phase 10 endpoints

Tasks: `/tasks`, `/tasks/{id}`, `/tasks/{id}/transition`, client `/client-portal/tasks`.
Notifications: `/notifications`, `/notifications/{id}/read`, `/notifications/read-all`, `/notifications/preferences`, template/delivery admin endpoints and client notification equivalents.
Reminders: `/reminder-policies`, archive/update/list and schedule read endpoints.

## Phase 11 endpoints

- `GET /onboardings/{id}/review` — final-review/readiness evidence for review/approve/activation-authorized staff.
- `POST /onboardings/{id}/review` — record final-review start (`ONBOARDING_REVIEW`).
- `POST /onboardings/{id}/approve` — approve + complete onboarding and move project to READY (`ONBOARDING_APPROVE`).
- `POST /onboardings/{id}/request-revision` — reopen supported selected requirements (`ONBOARDING_REVIEW`).
- `POST /projects/{id}/activate` — independent READY → ACTIVE command (`PROJECT_ACTIVATE`).

Final approval and activation are intentionally not one endpoint.
