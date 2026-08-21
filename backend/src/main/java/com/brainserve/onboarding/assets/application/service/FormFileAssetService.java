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
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRequirementRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetReviewRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetVersionRepository;
import com.brainserve.onboarding.assets.infrastructure.storage.AssetObjectStorage;
import com.brainserve.onboarding.assets.infrastructure.storage.ObjectMissingException;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.forms.application.service.FormSubmissionService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

/** Secure Asset pipeline adapter for FILE questions. Form state remains owned by the Forms module. */
@Service
public class FormFileAssetService {
    private static final Logger log = LoggerFactory.getLogger(FormFileAssetService.class);
    private static final String META_VERSION = "asset-version-id";

    private final FormSubmissionService forms;
    private final OnboardingStepAccessService stepAccess;
    private final AssetRequirementRepository requirements;
    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetReviewRepository reviews;
    private final AssetObjectStorage storage;
    private final AssetStorageProperties storageProperties;
    private final AssetPolicy policy;
    private final AssetFilenamePolicy filenames;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public FormFileAssetService(FormSubmissionService forms, OnboardingStepAccessService stepAccess,
                                AssetRequirementRepository requirements, AssetRepository assets,
                                AssetVersionRepository versions, AssetReviewRepository reviews,
                                AssetObjectStorage storage, AssetStorageProperties storageProperties,
                                AssetPolicy policy, AssetFilenamePolicy filenames, ActivityTimelineService activity,
                                AuditService audit, OutboxService outbox, Clock clock) {
        this.forms = forms;
        this.stepAccess = stepAccess;
        this.requirements = requirements;
        this.assets = assets;
        this.versions = versions;
        this.reviews = reviews;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.policy = policy;
        this.filenames = filenames;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssetStepResponse get(ClientPrincipal principal, UUID projectId, UUID stepId, UUID fieldId) {
        var context = forms.fileUploadContext(principal, projectId, stepId, fieldId);
        // Include an earlier response-version upload for the same immutable questionnaire field.
        // Revision drafts may legitimately carry the previous CLEAN file reference forward.
        List<AssetRequirement> scoped = requirements
                .findAllByOrganizationIdAndStepInstanceIdAndFormFieldIdOrderByCreatedAtDescIdDesc(
                        principal.organizationId(), stepId, fieldId);
        Asset asset = scoped.stream().map(r -> assets.findByOrganizationIdAndRequirementId(principal.organizationId(), r.getId()).orElse(null))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        AssetRequirement requirement = asset == null ? null : requirements.findByOrganizationIdAndId(principal.organizationId(), asset.getRequirementId()).orElse(null);
        return response(context, requirement, asset);
    }

    @Transactional
    public AssetUploadUrlResponse requestUpload(ClientPrincipal principal, UUID projectId, UUID stepId, UUID fieldId,
                                                String idempotencyKey, RequestAssetUploadRequest request,
                                                HttpServletRequest servletRequest) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        // Serialize upload-attempt creation for this form step. This avoids duplicate requirement rows under concurrent requests.
        stepAccess.requireForProjectForUpdate(principal.organizationId(), projectId, stepId);
        var context = forms.fileUploadContext(principal, projectId, stepId, fieldId);
        AssetPolicy.Policy filePolicy = policy.fromConfiguration(context.configuration());
        policy.validateRequestedFile(filePolicy, request.contentType(), request.sizeBytes());
        String sanitized = filenames.sanitize(request.filename());
        String requirementKey = "FORM_FILE_" + fieldId + "_" + digestPrefix(key);
        AssetRequirement requirement = requirements.findForUpdate(principal.organizationId(), stepId, requirementKey).orElse(null);
        Instant now = clock.instant();
        if (requirement == null) {
            requirement = requirements.saveAndFlush(new AssetRequirement(UUID.randomUUID(), principal.organizationId(), projectId,
                    context.onboardingId(), stepId, context.submissionId(), fieldId, requirementKey, context.label(), context.helpText(),
                    filePolicy.allowedMimeTypesJson(), filePolicy.maxFileSizeBytes(), context.required(), false, principal.userId(), now));
        }
        Asset asset = assets.findForUpdateByRequirement(principal.organizationId(), requirement.getId()).orElse(null);
        if (asset == null) {
            asset = assets.saveAndFlush(new Asset(UUID.randomUUID(), principal.organizationId(), requirement.getId(), projectId,
                    context.onboardingId(), stepId, principal.userId(), AssetActorType.CLIENT, now));
        }

        AssetVersion sameRequest = versions.findByOrganizationIdAndRequirementIdAndUploadIdempotencyKey(
                principal.organizationId(), requirement.getId(), key).orElse(null);
        if (sameRequest != null) {
            if (sameRequest.getStatus() != AssetVersionStatus.PENDING_UPLOAD || sameRequest.uploadExpired(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key belongs to an upload request that can no longer be replayed.");
            }
            return presign(sameRequest, durationUntil(now, sameRequest.getUploadExpiresAt()));
        }

        AssetVersion pending = versions.findFirstByOrganizationIdAndAssetIdAndStatusOrderByVersionNumberDesc(
                principal.organizationId(), asset.getId(), AssetVersionStatus.PENDING_UPLOAD).orElse(null);
        if (pending != null && !pending.uploadExpired(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSET_UPLOAD_ALREADY_PENDING", "Complete or wait for the current upload authorization to expire.");
        }
        if (pending != null) {
            pending.rejected("Upload authorization expired before use.", null, null, now);
            versions.saveAndFlush(pending);
        }

        int number = versions.findFirstByOrganizationIdAndAssetIdOrderByVersionNumberDesc(principal.organizationId(), asset.getId())
                .map(value -> value.getVersionNumber() + 1).orElse(1);
        UUID versionId = UUID.randomUUID();
        String objectKey = objectKey(principal.organizationId(), projectId, asset.getId(), versionId);
        Instant expiresAt = now.plus(storageProperties.uploadUrlTtl());
        AssetVersion version = versions.saveAndFlush(new AssetVersion(versionId, principal.organizationId(), asset.getId(), requirement.getId(),
                projectId, number, request.filename(), sanitized, storage.bucket(), objectKey, request.sizeBytes(), request.contentType(),
                request.sha256(), key, expiresAt, principal.userId(), now));
        audit.recordClient(principal.organizationId(), principal.userId(), "FORM_FILE_UPLOAD_AUTHORIZED", "ASSET", asset.getId(), null,
                Map.of("assetVersionId", versionId, "stepId", stepId, "fieldId", fieldId, "submissionId", context.submissionId()), servletRequest);
        return presign(version, storageProperties.uploadUrlTtl());
    }

    @Transactional
    public AssetStepResponse completeUpload(ClientPrincipal principal, UUID projectId, UUID stepId, UUID fieldId,
                                            CompleteAssetUploadRequest request, HttpServletRequest servletRequest) {
        stepAccess.requireForProjectForUpdate(principal.organizationId(), projectId, stepId);
        var context = forms.fileUploadContext(principal, projectId, stepId, fieldId);
        AssetVersion version = versions.findForUpdate(principal.organizationId(), request.assetVersionId()).orElseThrow(FormFileAssetService::notFound);
        AssetRequirement requirement = requirements.findByOrganizationIdAndId(principal.organizationId(), version.getRequirementId()).orElseThrow(FormFileAssetService::notFound);
        if (!projectId.equals(version.getProjectId()) || !stepId.equals(requirement.getStepInstanceId()) || !fieldId.equals(requirement.getFormFieldId())
                || !context.submissionId().equals(requirement.getFormSubmissionId())) throw notFound();
        Asset asset = assets.findForUpdate(principal.organizationId(), version.getAssetId()).orElseThrow(FormFileAssetService::notFound);
        if (version.getStatus() != AssetVersionStatus.PENDING_UPLOAD) {
            if (List.of(AssetVersionStatus.SCAN_PENDING, AssetVersionStatus.SCANNING, AssetVersionStatus.CLEAN).contains(version.getStatus())) {
                return response(context, requirement, asset);
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
        catch (ObjectMissingException ex) { throw new ApiException(HttpStatus.CONFLICT, "ASSET_UPLOAD_NOT_FOUND", "The file has not reached secure storage yet."); }
        if (object.sizeBytes() != version.getExpectedSizeBytes() || !version.getId().toString().equals(object.metadata().get(META_VERSION))) {
            safeDelete(version);
            version.rejected("Stored object did not match the authorized upload.", null, null, now);
            versions.saveAndFlush(version);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_UPLOAD_VERIFICATION_FAILED", "The uploaded object could not be verified.");
        }
        try {
            version.uploaded(object.sizeBytes(), object.eTag(), now);
            version.queueScan(now);
            asset.uploaded(version.getId(), principal.userId(), AssetActorType.CLIENT, now);
            asset.scanStarted(now);
        } catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        versions.saveAndFlush(version);
        assets.saveAndFlush(asset);
        outbox.record(principal.organizationId(), "ASSET_UPLOADED", "ASSET", asset.getId(), Map.of(
                "assetId", asset.getId(), "assetVersionId", version.getId(), "projectId", projectId, "stepId", stepId,
                "formSubmissionId", context.submissionId(), "formFieldId", fieldId));
        activity.recordClient(principal.organizationId(), context.clientId(), projectId, principal.userId(), "FORM_FILE_UPLOADED",
                "ASSET", asset.getId(), "Questionnaire file uploaded and queued for security scanning", Map.of("fieldId", fieldId, "filename", version.getSanitizedFilename()));
        audit.recordClient(principal.organizationId(), principal.userId(), "FORM_FILE_UPLOADED", "ASSET", asset.getId(), null,
                Map.of("assetVersionId", version.getId(), "fieldId", fieldId, "status", asset.getStatus()), servletRequest);
        return response(context, requirement, asset);
    }

    @Transactional(readOnly = true)
    public AssetDownloadUrlResponse download(ClientPrincipal principal, UUID projectId, UUID stepId, UUID fieldId, UUID assetId, UUID versionId) {
        var context = forms.fileReadContext(principal, projectId, stepId, fieldId);
        Asset asset = assets.findByOrganizationIdAndId(principal.organizationId(), assetId).orElseThrow(FormFileAssetService::notFound);
        AssetRequirement requirement = requirements.findByOrganizationIdAndId(principal.organizationId(), asset.getRequirementId()).orElseThrow(FormFileAssetService::notFound);
        // Do not bind downloads to only the latest DRAFT submission. A revision may reuse an earlier clean
        // response-version file, but it must still belong to this exact project/step/field scope.
        if (!projectId.equals(asset.getProjectId()) || !stepId.equals(asset.getStepInstanceId()) || !fieldId.equals(requirement.getFormFieldId())
                || requirement.getFormSubmissionId() == null) throw notFound();
        AssetVersion version = versions.findByOrganizationIdAndId(principal.organizationId(), versionId).orElseThrow(FormFileAssetService::notFound);
        if (!assetId.equals(version.getAssetId()) || version.getStatus() != AssetVersionStatus.CLEAN) throw notFound();
        String mime = version.getDetectedMimeType() == null ? "application/octet-stream" : version.getDetectedMimeType();
        var signed = storage.presignDownload(version.getStorageBucket(), version.getObjectKey(), version.getSanitizedFilename(), mime, storageProperties.downloadUrlTtl());
        return new AssetDownloadUrlResponse(signed.url(), signed.expiresAt(), version.getSanitizedFilename(), mime);
    }

    private AssetStepResponse response(FormSubmissionService.FormFileUploadContext context, AssetRequirement requirement, Asset asset) {
        AssetPolicy.Policy filePolicy = policy.fromConfiguration(context.configuration());
        List<AssetVersionResponse> versionResponses = asset == null ? List.of() : versions
                .findAllByOrganizationIdAndAssetIdOrderByVersionNumberDesc(context.organizationId(), asset.getId()).stream()
                .map(FormFileAssetService::versionResponse).toList();
        List<AssetReviewResponse> reviewResponses = asset == null ? List.of() : reviews
                .findAllByOrganizationIdAndAssetIdOrderByCreatedAtDescIdDesc(context.organizationId(), asset.getId()).stream()
                .map(FormFileAssetService::reviewResponse).toList();
        return new AssetStepResponse(requirement == null ? null : requirement.getId(), context.projectId(), context.onboardingId(), context.stepId(),
                context.label(), context.helpText(), context.required(), false, filePolicy.maxFileSizeBytes(),
                filePolicy.allowedMimeTypes().stream().sorted().toList(), asset == null ? null : asset.getId(),
                asset == null ? AssetStatus.REQUESTED : asset.getStatus(), asset == null ? null : asset.getCurrentVersionId(),
                asset == null ? 0 : asset.getVersion(), versionResponses, reviewResponses);
    }

    private AssetUploadUrlResponse presign(AssetVersion version, Duration ttl) {
        Map<String, String> metadata = Map.of(META_VERSION, version.getId().toString());
        var signed = storage.presignUpload(version.getObjectKey(), version.getDeclaredMimeType(), version.getExpectedSizeBytes(), ttl, metadata);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        signed.headers().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("content-type") || lower.startsWith("x-amz-meta-")) headers.put(name, values);
        });
        return new AssetUploadUrlResponse(version.getAssetId(), version.getId(), version.getVersionNumber(), version.getSanitizedFilename(),
                signed.url(), signed.method(), headers, signed.expiresAt());
    }

    private void safeDelete(AssetVersion version) {
        try { storage.delete(version.getStorageBucket(), version.getObjectKey()); }
        catch (RuntimeException ex) { log.warn("Could not delete rejected form-file object for version {}: {}", version.getId(), ex.getMessage()); }
    }

    private static AssetVersionResponse versionResponse(AssetVersion v) {
        return new AssetVersionResponse(v.getId(), v.getVersionNumber(), v.getSanitizedFilename(), v.getExpectedSizeBytes(), v.getActualSizeBytes(),
                v.getDeclaredMimeType(), v.getDetectedMimeType(), v.getActualSha256(), v.getStatus(), v.getUploadedAt(), v.getScannedAt(),
                v.getScanAttemptCount(), v.getRejectionReason(), v.getCreatedAt(), v.getVersion());
    }
    private static AssetReviewResponse reviewResponse(AssetReview r) {
        return new AssetReviewResponse(r.getId(), r.getAssetVersionId(), r.getReviewerUserId(), r.getAction(), r.getNote(), r.getCreatedAt());
    }
    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "A valid Idempotency-Key is required for upload authorization.");
        }
        return value;
    }
    private static String digestPrefix(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
    private static Duration durationUntil(Instant now, Instant expiresAt) {
        Duration value = Duration.between(now, expiresAt);
        return value.isNegative() || value.isZero() ? Duration.ofSeconds(1) : value;
    }
    private static String objectKey(UUID organizationId, UUID projectId, UUID assetId, UUID versionId) {
        return "org/" + organizationId + "/projects/" + projectId + "/assets/" + assetId + "/versions/" + versionId + "/content";
    }
    private static ApiException unavailable() { return new ApiException(HttpStatus.CONFLICT, "ASSET_UPLOAD_UNAVAILABLE", "This file upload is no longer available."); }
    private static ApiException state(String message) { return new ApiException(HttpStatus.CONFLICT, "ASSET_STATE_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested file upload was not found."); }
}
