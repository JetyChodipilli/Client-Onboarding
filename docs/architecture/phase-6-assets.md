# Phase 6: Asset management

Scope: immutable asset requirements, private presigned uploads, versioned files, MIME/size/hash
validation, malware scanning, review/revision/approval, secure downloads and workflow integration.
Billing and all later phases remain outside this change.

Each FILE_UPLOAD workflow step pins an immutable tenant-owned asset requirement. One requirement
collects one file with replacement versions; use multiple steps for multiple required files. Published
workflow snapshots never change when a new requirement is created or an old requirement is archived.

The assets module reuses portal project/assignment authorization and onboarding's application boundary
for transactional step/readiness writes. It owns its requirements, assets, versions, reviews and outbox.
Generic step transitions cannot bypass file validation/review. Internal permissions are ASSET_READ,
ASSET_MANAGE and ASSET_REVIEW; manage/review require MFA. Client uploads use existing portal grants.

The storage adapter uses the AWS SDK v2 against S3-compatible storage with bucket versioning required.
Upload signatures bind the generated key, MIME, declared size, hash metadata and `If-None-Match: *`.
The signed condition rejects URL replay after the first write. Authorization occurs
before signing. The application pins the exact object version, streams it through bounded temporary
storage, validates its hash and detected MIME, then scans it using ClamAV INSTREAM. It never treats
file extensions, browser success callbacks or client-supplied scanner verdicts as proof of safety.

Scan work runs outside database transactions. A 300-second transactional lease serializes scan requests;
the result is accepted only for the same current version and lease. Crashed/failed work is retryable,
and cannot make content downloadable. Project locks, state rechecks and optimistic versions protect
upload/review/scan completion from concurrent hold/cancel/replacement/review. Workflow changes,
reviews, audit and durable asset events commit together. Delivery workers remain Phase 10.

Downloads require fresh tenant/project/assignment authorization and a clean scan. Short-lived URLs
reference only the pinned object version, force attachment/octet-stream delivery and never expose
an anonymous bucket. Quarantined/rejected/unscanned objects cannot receive a download URL. Reusing
an upload URL after scanning cannot replace the version that reviewers and clients download.

Development uses private MinIO and ClamAV services. Production must provision a private, versioned
bucket, restricted credentials, TLS storage endpoints, trusted-network scanner access, fresh malware
signatures, and lifecycle/retention rules. Disabling storage disables file operations with a controlled
error; there is no permissive scanner fallback. Existing snapshots without assetRequirementId need
an explicitly approved migration; new workflow versions can bind a requirement without rewriting them.

Supported file types deliberately exclude HTML, SVG, executables, archives and macro-bearing office
formats. Images, PDF, plain text and MP4 cover this phase's collection flow; adding another type requires
an allowlist/detection/security test. Asset requirements enforce a per-file size limit, up to 50 MiB.

References: [AWS presigning](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html),
[ClamAV protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html),
[ClamAV containers](https://docs.clamav.net/manual/Installing/Docker.html), and Apache Tika core MIME detection.

## Capacity, recovery and rollout

- Enable storage only after provisioning private bucket versioning, restricted credentials and scanner access.
  Flyway applies V8 during startup; existing V1–V7 checksums are unchanged. Back up the database before rollout.
- Deploy the backend and frontend together. Public API/storage origins are frontend build arguments.
  Grant asset permissions explicitly; bootstrap administrators receive the permission catalog on creation.
- Storage outages, disabled versioning, scanner timeouts and invalid protocol replies fail closed. A failed
  scan is retryable from the portal. An interrupted SCANNING lease becomes reclaimable after five minutes.
- At most two scans run concurrently per application instance. Storage calls and scanning have bounded
  timeouts; temporary storage is capped at the declared size (maximum 50 MiB) and cleaned in a finally block.
  Production ingress must bound aggregate upload rate/storage consumption across application instances.
- Upload URLs expire after 600 seconds; download URLs after 60 seconds. An already-issued download remains
  usable until expiry even if access is subsequently revoked. Do not log signed URLs or file contents.
- Keep the scanner on a trusted private network and continuously refresh malware signatures. CI disables
  only the background updater and uses the image's bundled real signatures, including EICAR detection.
- Versions and reviews preserve history. This phase does not automatically delete orphaned/quarantined
  objects or configure production retention; operators must define lifecycle rules consistent with audit
  and retention requirements. Do not delete referenced object versions before their retention period ends.
- Files are dedicated FILE_UPLOAD steps, not embedded FILE fields in questionnaires. Existing unbound
  immutable FILE_UPLOAD snapshots cannot use generic transitions as a bypass. Create a bound workflow
  version for new onboardings; migrating historical snapshots requires a separate explicit decision.
- Rollback: disable asset entry points/storage and roll back application images after assessing V8 writes.
  Do not drop asset history or reverse an applied migration. Keep V8 in the database and use forward fixes.

Health readiness continues to report the database dependency. Asset operations surface storage/scanner
failure as controlled API errors or retryable scan states; they do not claim the provider is healthy merely
because the application's database readiness probe passes.
