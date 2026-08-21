# Phase 11 — Readiness Engine, Review & Project Activation

## PRD boundary

Readiness remains a deterministic calculation over the immutable onboarding snapshot: every applicable blocking step must be `COMPLETED`. Final review is a separate privileged decision. Approval completes onboarding and moves the project to `READY`; project activation is a separate `PROJECT_ACTIVATE` operation.

## Implemented source

- Recomputed readiness policy and automatic `AWAITING_INTERNAL_REVIEW` lifecycle entry.
- Final-review checklist with blocking/required counts, revision eligibility and append-only review history.
- `ONBOARDING_REVIEW` start/revision and `ONBOARDING_APPROVE` approval boundaries.
- Feature-specific revision handlers for revisable Forms, Assets, Platform Access and Tasks.
- Payment and Contract facts cannot be manually rewritten from final review.
- Final approval requires all blocking and all required requirements complete with no outstanding final-review revision.
- Approval transitions onboarding through `APPROVED -> COMPLETED` and project `ONBOARDING -> READY` in one transaction.
- `PROJECT_ACTIVATE` is independent and transitions `READY -> ACTIVE` with optimistic concurrency.
- V12 append-only `onboarding_reviews` and database transition guard.
- V14 forward hardening additionally requires `READY` to be entered only from `ONBOARDING`.
- Responsive internal project-review screen and Phase 11 Playwright source.

## Defects fixed during release audit

- Strengthened the database project transition guard so a completed onboarding alone cannot justify an arbitrary state jump to `READY`; the old project state must be `ONBOARDING`.
- Added regression source proving a direct `DRAFT -> READY` database transition is rejected after onboarding completion.
- Preserved final-review revisions as a lifecycle gate separate from mathematical blocking readiness, preventing non-blocking review revisions from prematurely returning to final review.

## Verification status

Java source/test parsing is clean at the grammar level. Full Maven/Testcontainers/Flyway and Playwright execution is unavailable in this runtime, so strict Definition of Done remains unverified here.
