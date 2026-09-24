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
Upload signatures bind the generated key, MIME, declared size and hash metadata. Authorization occurs
before signing. The application pins the exact object version, streams it through bounded temporary
storage, validates its hash and detected MIME, then scans it using ClamAV INSTREAM. It never treats
file extensions, browser success callbacks or client-supplied scanner verdicts as proof of safety.

Scan work runs outside database transactions. A short transactional lease serializes scan requests;
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
