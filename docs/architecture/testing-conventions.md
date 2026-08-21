# Testing Conventions

Use the lowest-cost reliable layer that proves the rule.

- **Unit:** state machines, readiness/revision gates, validation, reminder suppression, calculations.
- **Integration:** Spring Boot + PostgreSQL/Testcontainers + Flyway; tenant/resource constraints, database triggers and cross-module transactions.
- **API/security:** authentication, authorization, cross-tenant/IDOR, validation, pagination, idempotency, concurrency, webhook protection.
- **Frontend:** Vitest/RTL where component logic benefits.
- **E2E:** Playwright for critical browser journeys, responsive layout and console errors.

Naming: unit `*Test`; integration `*IT`; browser `*.spec.ts`.

## Phase 10 regression expectations

Cover task lifecycle/assignment, client grant revocation, notification deduplication/preferences, bounded delivery retry/dead state, stale worker recovery, reminder max count/business hours/responsibility routing and suppression for completed/cancelled/paused/invalid targets.

## Phase 11 expectations

At minimum prove:
- readiness uses blocking steps only;
- zero-blocker workflow enters review correctly after onboarding becomes active;
- non-blocking final-review revision cannot prematurely return to review;
- unsupported/final facts cannot be reopened;
- final approval produces onboarding COMPLETED + project READY atomically;
- cross-tenant review access is denied/not-found;
- stale review/activation versions return controlled conflict;
- only `PROJECT_ACTIVATE` can perform READY → ACTIVE;
- review evidence cannot be rewritten;
- database guard rejects READY without completed onboarding and illegal ACTIVE transitions;
- Playwright shows readiness evidence, revision/approval, READY and separate activation.

## Completion truth

A static parser/grep/config check is useful but is not a substitute for `mvn verify`, clean Flyway/Testcontainers execution, Next production build, Vitest and Playwright.

## Release verification

Phase 13 requires the complete Maven/Testcontainers/Flyway and Next/Vitest/Playwright gates plus CodeQL/dependency review. Static parsing or source inspection is supporting evidence only and cannot replace an executable PASS.
