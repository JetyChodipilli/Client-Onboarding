package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.assets.api.request.CompleteAssetUploadRequest;
import com.brainserve.onboarding.assets.api.request.RequestAssetUploadRequest;
import com.brainserve.onboarding.assets.api.response.AssetDownloadUrlResponse;
import com.brainserve.onboarding.assets.api.response.AssetReviewResponse;
import com.brainserve.onboarding.assets.api.response.AssetStepResponse;
import com.brainserve.onboarding.assets.api.response.AssetUploadUrlResponse;
import com.brainserve.onboarding.assets.api.response.AssetVersionResponse;
import com.brainserve.onboarding.assets.domain.model.Asset;
import com.brainserve.onboarding.assets.domain.model.AssetActorType;
import com.brainserve.onboarding.assets.domain.model.AssetRequirement;
import com.brainserve.onboarding.assets.domain.model.AssetReview;
import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import com.brainserve.onboarding.assets.domain.model.AssetVersion;
import com.brainserve.onboarding.assets.domain.model.AssetVersionStatus;
import com.brainserve.onboarding.assets.infrastructure.config.AssetStorageProperties;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetReviewRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetVersionRepository;
import com.brainserve.onboarding.assets.infrastructure.storage.AssetObjectStorage;
import com.brainserve.onboarding.assets.infrastructure.storage.ObjectMissingException;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.application.service.WorkflowActorType;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientAssetService {
    private static final Logger log = LoggerFactory.getLogger(ClientAssetService.class);
    private static final String META_VERSION = "asset-version-id";
    private static final List<OnboardingStepStatus> EDITABLE = List.of(
            OnboardingStepStatus.AVAILABLE, OnboardingStepStatus.IN_PROGRESS,
            OnboardingStepStatus.NEEDS_REVISION, OnboardingStepStatus.FAILED);

    private final AssetRequirementService requirementService;
    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetReviewRepository reviews;
    private final ClientPortalAccessService portalAccess;
    private final OnboardingStepCommandService stepCommands;
    private final AssetObjectStorage storage;
    private final AssetStorageProperties storageProperties;
    private final AssetPolicy policy;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public ClientAssetService(AssetRequirementService requirementService, AssetRepository assets,
                              AssetVersionRepository versions, AssetReviewRepository reviews,
                              ClientPortalAccessService portalAccess, OnboardingStepCommandService stepCommands,
                              AssetObjectStorage storage, AssetStorageProperties storageProperties, AssetPolicy policy,
                              ActivityTimelineService activity, AuditService audit, OutboxService outbox, Clock clock) {
        this.requirementService = requirementService;
        this.assets = assets;
        this.versions = versions;
        this.reviews = reviews;
        this.portalAccess = portalAccess;
        this.stepCommands = stepCommands;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.policy = policy;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssetStepResponse get(ClientPrincipal principal, UUID projectId, UUID stepId) {
        requireProjectAccess(principal, projectId);
        AssetRequirementService.Context context = requirementService.requireWorkflowRequirement(
                principal.organizationId(), projectId, stepId);
        if (!context.step().clientVisible()) throw notFound();
        Asset asset = context.requirement() == null ? null : assets.findByOrganizationIdAndRequirementId(
                principal.organizationId(), context.requirement().getId()).orElse(null);
        return response(context, asset);
    }

    @Transactional
    public AssetUploadUrlResponse requestUpload(ClientPrincipal principal, UUID projectId, UUID stepId,
                                                String idempotencyKey, RequestAssetUploadRequest request,
                                                HttpServletRequest servletRequest) {
        requireAdminProjectAccess(principal, projectId);
        String key = normalizeIdempotencyKey(idempotencyKey);
        AssetRequirementService.Context context = requirementService.ensureWorkflowRequirement(
                principal.organizationId(), projectId, stepId, principal.userId());
        if (!context.step().clientVisible()) throw notFound();
        if (!EDITABLE.contains(context.step().status())) throw unavailable();
        policy.validateRequestedFile(context.policy(), request.contentType(), request.sizeBytes());
        String filename = AssetFilenamePolicy.sanitize(request.filename());
        Instant now = clock.instant();

        Asset asset = assets.findForUpdateByRequirement(principal.organizationId(), context.requirement().getId()).orElse(null);
        if (asset == null) {
            asset = assets.saveAndFlush(new Asset(UUID.randomUUID(), principal.organizationId(), context.requirement().getId(),
                    projectId, context.step().onboardingId(), stepId, principal.userId(), AssetActorType.CLIENT, now));
        }
        if (asset.getStatus() == AssetStatus.APPROVED || asset.getStatus() == AssetStatus.UNDER_REVIEW
                || asset.getStatus() == AssetStatus.SUBMITTED || asset.getStatus() == AssetStatus.SCANNING) {
            throw unavailable();
        }

        AssetVersion sameRequest = versions.findByOrganizationIdAndRequirementIdAndUploadIdempotencyKey(
                principal.organizationId(), context.requirement().getId(), key).orElse(null);
        if (sameRequest != null) {
            if (sameRequest.getStatus() == AssetVersionStatus.PENDING_UPLOAD && !sameRequest.uploadExpired(now)) {
                return presign(sameRequest, durationUntil(now, sameRequest.getUploadExpiresAt()));
            }
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "This upload request key has already been consumed. Start a new upload with a new Idempotency-Key.");
        }

        AssetVersion pending = versions.findFirstByOrganizationIdAndAssetIdAndStatusOrderByVersionNumberDesc(
                principal.organizationId(), asset.getId(), AssetVersionStatus.PENDING_UPLOAD).orElse(null);
        if (pending != null) {
            if (!pending.uploadExpired(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "ASSET_UPLOAD_ALREADY_PENDING",
                        "An upload is already authorized for this requirement. Complete it or wait for it to expire.");
            }
            pending.rejected("Upload authorization expired before completion.", null, null, now);
            versions.saveAndFlush(pending);
        }

        if (context.step().status() == OnboardingStepStatus.AVAILABLE
                || context.step().status() == OnboardingStepStatus.NEEDS_REVISION
                || context.step().status() == OnboardingStepStatus.FAILED) {
            stepCommands.transition(principal.organizationId(), context.step().onboardingId(), stepId,
                    OnboardingStepStatus.IN_PROGRESS, principal.userId(), WorkflowActorType.CLIENT);
        }

        int versionNumber = versions.findFirstByOrganizationIdAndAssetIdOrderByVersionNumberDesc(principal.organizationId(), asset.getId())
                .map(value -> value.getVersionNumber() + 1).orElse(1);
        UUID versionId = UUID.randomUUID();
        String objectKey = objectKey(principal.organizationId(), projectId, asset.getId(), versionId);
        Instant expiresAt = now.plus(storageProperties.uploadUrlTtl());
        AssetVersion version = versions.saveAndFlush(new AssetVersion(versionId, principal.organizationId(), asset.getId(),
                context.requirement().getId(), projectId, versionNumber, request.filename(), filename, storage.bucket(), objectKey,
                request.sizeBytes(), request.contentType(), request.sha256(), key, expiresAt, principal.userId(), now));

        activity.recordClient(principal.organizationId(), null, projectId, principal.userId(), "ASSET_UPLOAD_AUTHORIZED",
                "ASSET", asset.getId(), "Secure asset upload authorized",
                Map.of("stepId", stepId, "versionNumber", versionNumber, "filename", filename));
        audit.recordClient(principal.organizationId(), principal.userId(), "ASSET_UPLOAD_AUTHORIZED", "ASSET", asset.getId(), null,
                Map.of("stepId", stepId, "assetVersionId", versionId, "filename", filename, "sizeBytes", request.sizeBytes()), servletRequest);
        return presign(version, storageProperties.uploadUrlTtl());
    }

    @Transactional
    public AssetStepResponse completeUpload(ClientPrincipal principal, UUID projectId, UUID stepId,
                                            CompleteAssetUploadRequest request, HttpServletRequest servletRequest) {
        requireAdminProjectAccess(principal, projectId);
        AssetRequirementService.Context context = requirementService.ensureWorkflowRequirement(
                principal.organizationId(), projectId, stepId, principal.userId());
        if (!context.step().clientVisible()) throw notFound();
        AssetVersion version = versions.findForUpdate(principal.organizationId(), request.assetVersionId()).orElseThrow(ClientAssetService::notFound);
        if (!version.getProjectId().equals(projectId) || !version.getRequirementId().equals(context.requirement().getId())) throw notFound();
        if (version.getStatus() != AssetVersionStatus.PENDING_UPLOAD) {
            if (version.getStatus() == AssetVersionStatus.SCAN_PENDING || version.getStatus() == AssetVersionStatus.SCANNING
                    || version.getStatus() == AssetVersionStatus.CLEAN) {
                Asset existing = assets.findByOrganizationIdAndId(principal.organizationId(), version.getAssetId()).orElseThrow(ClientAssetService::notFound);
                return response(context, existing);
            }
            throw unavailable();
        }
        Instant now = clock.instant();
        if (version.uploadExpired(now)) {
            version.rejected("Upload authorization expired before completion.", null, null, now);
            versions.saveAndFlush(version);
            throw new ApiException(HttpStatus.GONE, "ASSET_UPLOAD_EXPIRED", "The upload authorization expired. Start a new upload.");
        }

        AssetObjectStorage.StoredObject object;
        try { object = storage.head(version.getStorageBucket(), version.getObjectKey()); }
        catch (ObjectMissingException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSET_UPLOAD_NOT_FOUND", "The file has not reached secure storage yet.");
        }
        if (object.sizeBytes() != version.getExpectedSizeBytes()) {
            safeDelete(version);
            version.rejected("Stored file size does not match the authorized upload.", null, null, now);
            versions.saveAndFlush(version);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_UPLOAD_SIZE_MISMATCH", "The uploaded file size did not match the authorized request.");
        }
        String metadataVersion = object.metadata().get(META_VERSION);
        if (!version.getId().toString().equals(metadataVersion)) {
            safeDelete(version);
            version.rejected("Stored object metadata did not match the upload authorization.", null, null, now);
            versions.saveAndFlush(version);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_UPLOAD_METADATA_MISMATCH", "The uploaded object could not be verified.");
        }

        Asset asset = assets.findForUpdate(principal.organizationId(), version.getAssetId()).orElseThrow(ClientAssetService::notFound);
        try {
            version.uploaded(object.sizeBytes(), object.eTag(), now);
            version.queueScan(now);
            asset.uploaded(version.getId(), principal.userId(), AssetActorType.CLIENT, now);
            asset.scanStarted(now);
        } catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        versions.saveAndFlush(version);
        assets.saveAndFlush(asset);
        outbox.record(principal.organizationId(), "ASSET_UPLOADED", "ASSET", asset.getId(),
                Map.of("assetId", asset.getId(), "assetVersionId", version.getId(), "projectId", projectId, "stepId", stepId));
        activity.recordClient(principal.organizationId(), null, projectId, principal.userId(), "ASSET_UPLOADED",
                "ASSET", asset.getId(), "Asset uploaded and queued for security scanning",
                Map.of("filename", version.getSanitizedFilename(), "versionNumber", version.getVersionNumber()));
        audit.recordClient(principal.organizationId(), principal.userId(), "ASSET_UPLOADED", "ASSET", asset.getId(), null,
                Map.of("assetVersionId", version.getId(), "status", asset.getStatus()), servletRequest);
        return response(context, asset);
    }

    @Transactional(readOnly = true)
    public AssetDownloadUrlResponse download(ClientPrincipal principal, UUID projectId, UUID assetId, UUID versionId) {
        requireProjectAccess(principal, projectId);
        Asset asset = assets.findByOrganizationIdAndId(principal.organizationId(), assetId).orElseThrow(ClientAssetService::notFound);
        if (!asset.getProjectId().equals(projectId)) throw notFound();
        AssetVersion version = versions.findByOrganizationIdAndId(principal.organizationId(), versionId).orElseThrow(ClientAssetService::notFound);
        if (!version.getAssetId().equals(assetId) || version.getStatus() != AssetVersionStatus.CLEAN) throw notFound();
        String mime = version.getDetectedMimeType() == null ? "application/octet-stream" : version.getDetectedMimeType();
        var signed = storage.presignDownload(version.getStorageBucket(), version.getObjectKey(), version.getSanitizedFilename(), mime,
                storageProperties.downloadUrlTtl());
        return new AssetDownloadUrlResponse(signed.url(), signed.expiresAt(), version.getSanitizedFilename(), mime);
    }

    private AssetUploadUrlResponse presign(AssetVersion version, Duration ttl) {
        Map<String, String> metadata = Map.of(META_VERSION, version.getId().toString());
        var signed = storage.presignUpload(version.getObjectKey(), version.getDeclaredMimeType(), version.getExpectedSizeBytes(), ttl, metadata);
        Map<String, List<String>> browserHeaders = new LinkedHashMap<>();
        signed.headers().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("content-type") || lower.startsWith("x-amz-meta-")) browserHeaders.put(name, values);
        });
        return new AssetUploadUrlResponse(version.getAssetId(), version.getId(), version.getVersionNumber(),
                version.getSanitizedFilename(), signed.url(), signed.method(), browserHeaders, signed.expiresAt());
    }

    private AssetStepResponse response(AssetRequirementService.Context context, Asset asset) {
        AssetRequirement requirement = context.requirement();
        List<AssetVersionResponse> versionResponses = asset == null ? List.of() : versions
                .findAllByOrganizationIdAndAssetIdOrderByVersionNumberDesc(asset.getOrganizationId(), asset.getId())
                .stream().map(ClientAssetService::versionResponse).toList();
        List<AssetReviewResponse> reviewResponses = asset == null ? List.of() : reviews
                .findAllByOrganizationIdAndAssetIdOrderByCreatedAtDescIdDesc(asset.getOrganizationId(), asset.getId())
                .stream().map(ClientAssetService::reviewResponse).toList();
        return new AssetStepResponse(requirement == null ? null : requirement.getId(), context.step().projectId(), context.step().onboardingId(),
                context.step().id(), context.step().name(), context.step().description(), context.step().required(), context.step().requiresReview(),
                context.policy().maxFileSizeBytes(), context.policy().allowedMimeTypes().stream().sorted().toList(),
                asset == null ? null : asset.getId(), asset == null ? AssetStatus.REQUESTED : asset.getStatus(),
                asset == null ? null : asset.getCurrentVersionId(), asset == null ? 0 : asset.getVersion(), versionResponses, reviewResponses);
    }

    private static AssetVersionResponse versionResponse(AssetVersion version) {
        return new AssetVersionResponse(version.getId(), version.getVersionNumber(), version.getSanitizedFilename(),
                version.getExpectedSizeBytes(), version.getActualSizeBytes(), version.getDeclaredMimeType(), version.getDetectedMimeType(),
                version.getActualSha256(), version.getStatus(), version.getUploadedAt(), version.getScannedAt(), version.getScanAttemptCount(),
                version.getRejectionReason(), version.getCreatedAt(), version.getVersion());
    }

    private static AssetReviewResponse reviewResponse(AssetReview review) {
        return new AssetReviewResponse(review.getId(), review.getAssetVersionId(), review.getReviewerUserId(), review.getAction(), review.getNote(), review.getCreatedAt());
    }

    private ClientPortalAccessService.ProjectAccess requireProjectAccess(ClientPrincipal principal, UUID projectId) {
        return portalAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
    }

    private void requireAdminProjectAccess(ClientPrincipal principal, UUID projectId) {
        if (requireProjectAccess(principal, projectId).accessLevel() != ClientProjectAccessLevel.CLIENT_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_TASK_NOT_ASSIGNED",
                    "This asset requirement is not assigned to your client-member account.");
        }
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "A valid Idempotency-Key is required for upload authorization.");
        }
        return value;
    }

    private static Duration durationUntil(Instant now, Instant expiresAt) {
        Duration value = Duration.between(now, expiresAt);
        return value.isNegative() || value.isZero() ? Duration.ofSeconds(1) : value;
    }

    private static String objectKey(UUID organizationId, UUID projectId, UUID assetId, UUID versionId) {
        return "org/" + organizationId + "/projects/" + projectId + "/assets/" + assetId + "/versions/" + versionId + "/content";
    }

    private void safeDelete(AssetVersion version) {
        try {
            storage.delete(version.getStorageBucket(), version.getObjectKey());
        } catch (RuntimeException ex) {
            // Deletion is best effort here: policy/state rejection still makes the object non-downloadable.
            log.warn("Could not delete rejected asset object for version {}: {}", version.getId(), ex.getMessage());
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STEP_NOT_AVAILABLE", "This asset requirement is not currently available for upload.");
    }
    private static ApiException state(String message) { return new ApiException(HttpStatus.CONFLICT, "ASSET_STATE_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested asset was not found."); }
}
