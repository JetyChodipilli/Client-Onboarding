# Phase 6 — Asset Management

PHASE COMPLETED:

Phase 6 — Asset Management. Backend, frontend, real storage, browser and container gates passed on the
implementation revision below. Delivery is through [PR #28](https://github.com/JetyChodipilli/Client-Onboarding/pull/28),
whose final revision must pass the same checks before merge into main.
Phase 7 has not started.

IMPLEMENTED:

- Immutable tenant-owned asset requirements and FILE_UPLOAD workflow bindings.
- Private, versioned S3 uploads with signed MIME, size, checksum metadata and a single-write condition.
- Authoritative byte-size, SHA-256 and MIME detection, real ClamAV scanning and quarantine/error states.
- Replacement versions, immutable review history, revision, approval and authorized exact-version downloads.
- Atomic workflow/readiness, audit and producer outbox updates; short scan leases and concurrent-command protection.
- Client upload/progress/recovery screens, internal requirement management and file review.
- Pinned source-built development S3 services, restricted bucket credentials, ClamAV, CI and operating documentation.
- Updated stale homepage/API phase metadata and their assertions. Prior phase behavior remains covered by regression CI.

DATABASE MIGRATIONS:

- `V8__asset_management.sql`; V1–V7 remain unchanged.
- Asset requirements, assets, asset_versions, asset_reviews and asset_outbox_events.
- Composite tenant foreign keys, current-version ownership, unique version numbers, optimistic versions,
  immutable requirement/file identity and object-version pinning, append-only review history.
- ASSET_READ and ASSET_MANAGE added; ASSET_REVIEW reuses the existing permission seeded by V2.
- Clean PostgreSQL migration through V8 and production Compose startup pass.

API ENDPOINTS:

14 operations across 13 new paths under `/api/v1`; 86 paths in the static OpenAPI document.

| Area | Routes |
|---|---|
| Requirements | `GET/POST /asset-requirements`; `POST /asset-requirements/{id}/archive` |
| Internal files | `GET /asset-responses/{stepId}` and `/versions`; `POST /review`, `/skip`, `/reopen`, `/versions/{fileId}/download` |
| Client files | `GET /client-portal/projects/{projectId}/assets/{stepId}` and `/versions`; `POST /upload-url`, `/submit`, `/versions/{fileId}/download` |

All routes use API envelopes, authoritative permissions and tenant/resource checks. Mutations and signed-URL
issuance use CSRF protection. Static response schemas exclude object keys, provider version IDs and scan leases;
the runtime OpenAPI smoke test probes the new requirement/review/upload routes.

UI SCREENS:

- `/app/assets`: searchable, paginated requirement catalog, create and archive.
- `/app/assets/responses/[stepId]`: clean-file review, revision, history, authorized downloads and exception actions.
- `/portal/projects/[projectId]/assets/[stepId]`: validation, upload progress, scanning, retry, replacements and history.
- Existing workflow/project/portal screens bind requirements and link to dedicated file flows.
- Status, project progress, next action, waiting party, blocker, deadline and help remain explicit.

TESTS ADDED:

- Asset policy tests; PostgreSQL integration coverage for lifecycle/history, isolation, permissions, CSRF,
  MIME/hash validation, scanner failures, quarantine, stale/concurrent submission, project hold during scan,
  cancelled projects, scan-lease recovery, immutable bindings, skip/reopen and automatic completion.
- Real S3-compatible provider tests for signed constraints, private access, version pinning, replay rejection,
  attachment headers, disabled storage and unversioned buckets.
- Real socket-protocol tests for ClamAV clean/infected/error/truncated responses.
- Frontend validation and transfer tests; runtime OpenAPI and migration assertions extended through V8.

PLAYWRIGHT SCENARIOS:

- Five new asset scenarios across desktop, tablet, mobile portrait and landscape: upload/history,
  validation/conflict recovery, quarantine, reviewer feedback and catalog/loading/empty/denied states.
- Full live journey extends MFA, SMTP invitation and questionnaires with real S3 uploads, EICAR quarantine,
  clean-file submission, revision/replacement, approval, exact signed download and 100% progress.
- 129 browser scenarios passed with no retries; three duplicate bootstrap scenarios are intentionally skipped
  outside desktop. Responsive asset scenarios execute in all four viewports.
- Inspected live approved-file, mobile submission/quarantine, tablet review and landscape requirement captures.
  Corrected the skip-link hiding style exposed by scrolling and added portal keyboard-navigation coverage.

SECURITY VALIDATION:

- Tenant-scoped repositories, composite foreign keys, portal grants and assignment checks.
- 401/403/secure 404 responses, CSRF, MFA for manage/review and no automatic grants to existing roles.
- MIME allowlist excludes active HTML/SVG, executables, archives and macro-bearing documents.
- Bounded streams, temporary files, storage timeouts, scanner deadline and two concurrent scans per instance.
- Scan results cannot bypass project hold/cancel, stale versions or a newer scan lease.
- Generic workflow transitions cannot complete FILE_UPLOAD work; clients cannot approve files.
- Downloads require clean scans and reference the immutable provider version, with a 60-second expiry.
- Signed upload headers bind a generated key; URLs are single-write and expire after 600 seconds.
- No raw file bytes, signed URLs or secrets enter audit/outbox records. No anonymous bucket access.

DESIGN PATTERNS USED:

- Modular monolith, application services, tenant-scoped repositories and workflow validator SPI.
- Required storage/scanner ports with real adapters; test fakes are restricted to test configuration.
- Optimistic versions, project row locking, bounded leases, immutable history and transactional producer outbox.
- Native file input/progress/history controls and existing design tokens; no new frontend dependency.

KNOWN LIMITATIONS:

- One file per workflow step, with up to 1,000 replacement versions and a 50 MiB per-file ceiling.
- Files use dedicated FILE_UPLOAD steps; embedded questionnaire FILE fields are not implemented.
- Production operators provision a maintained private versioned S3 service, TLS, signature updates,
  retention/lifecycle policies and aggregate ingress/storage limits. Bundled storage is for development/tests.
- Already-issued downloads remain valid for at most 60 seconds after access revocation.
- Interrupted scans can be retried after the five-minute lease expires. Scan capacity is per application instance.
- Legacy unbound FILE_UPLOAD snapshots need an explicitly approved migration; new versions can bind requirements.
- Event delivery workers remain Phase 10; final activation Phase 11; production hardening Phase 13.

PRD ITEMS COMPLETED:

Asset requirements, presigned upload, object storage, metadata, validation, malware scanning abstraction and
real adapter, versions, review/revision/approval, secure download, workflow integration and phase documentation.

PRD ITEMS REMAINING:

Phases 7–13 remain outside this implementation. Embedded questionnaire FILE fields are still unsupported;
secure files are collected through the dedicated FILE_UPLOAD workflow steps described above.

BUILD STATUS:

PASS — Maven verification, frontend lint/typecheck/production build and both application container builds.

TEST STATUS:

PASS — 82 backend tests with zero skips, 18 frontend unit tests, 129 browser scenarios and clean Compose
startup. Three duplicate live bootstrap runs are intentionally skipped outside the desktop viewport.

READY FOR NEXT PHASE:

YES — after the final PR checks and merge. Do not start Phase 7 automatically.

## Verification history

- Runs 36012748135 and 36092061432 exposed unavailable upstream MinIO registry images. Application,
  PostgreSQL, migration, architecture and scanner-protocol tests passed; the storage container could not be pulled.
- The fix builds pinned upstream source releases instead of substituting a fake provider or skipping S3 assertions.
- Run 36092904077 passed backend, frontend and container jobs. Browser setup exposed Compose waiting for
  the one-shot storage initializer to remain running after its successful exit. The browser job now starts
  storage/scanner services, then runs initialization as a separate command with its exit status enforced.
- Implementation revision `934df6cc28ecea7d6f3780859c708370c95320e5` passed all four jobs in
  [CI run 36381813000](https://github.com/JetyChodipilli/Client-Onboarding/actions/runs/36381813000).
  The live journey detected EICAR using ClamAV, quarantined it, reviewed a safe replacement, requested
  revision, approved a subsequent version and downloaded the exact expected bytes. No browser retries
  or unexplained application ERROR entries occurred.
- Artifact `phase-6-browser-evidence` from that run contains responsive screenshots and the Playwright report.
  Final revision checks, including the screenshot-driven skip-link correction, are attached to PR #28.
- Run 36418446205 passed backend, frontend and container checks plus 125 browser scenarios. The four new
  keyboard assertions exposed the Next.js development indicator taking first focus. The failure screenshot
  confirmed focus on that toolbar; disabling the supported `devIndicators` option removes development-only
  controls from the product tab order without suppressing errors or weakening the assertion. All checks
  are rerun on the corrected revision before merge.

See [architecture/rollout](../architecture/phase-6-assets.md) and [design/Ponytail review](phase-6-design-audit.md).
