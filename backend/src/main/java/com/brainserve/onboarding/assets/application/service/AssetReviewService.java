package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.assets.api.response.AssetDetailResponse;
import com.brainserve.onboarding.assets.api.response.AssetDownloadUrlResponse;
import com.brainserve.onboarding.assets.api.response.AssetReviewResponse;
import com.brainserve.onboarding.assets.api.response.AssetVersionResponse;
import com.brainserve.onboarding.assets.api.response.AssetSummaryResponse;
import com.brainserve.onboarding.assets.domain.model.Asset;
import com.brainserve.onboarding.assets.domain.model.AssetActorType;
import com.brainserve.onboarding.assets.domain.model.AssetRequirement;
import com.brainserve.onboarding.assets.domain.model.AssetReview;
import com.brainserve.onboarding.assets.domain.model.AssetReviewAction;
import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import com.brainserve.onboarding.assets.domain.model.AssetVersion;
import com.brainserve.onboarding.assets.domain.model.AssetVersionStatus;
import com.brainserve.onboarding.assets.infrastructure.config.AssetStorageProperties;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRequirementRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetReviewRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetVersionRepository;
import com.brainserve.onboarding.assets.infrastructure.storage.AssetObjectStorage;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetReviewService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AssetRepository assets;
    private final AssetRequirementRepository requirements;
    private final AssetVersionRepository versions;
    private final AssetReviewRepository reviews;
    private final OnboardingStepCommandService stepCommands;
    private final AssetObjectStorage storage;
    private final AssetStorageProperties storageProperties;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public AssetReviewService(AssetRepository assets, AssetRequirementRepository requirements, AssetVersionRepository versions,
                              AssetReviewRepository reviews, OnboardingStepCommandService stepCommands,
                              AssetObjectStorage storage, AssetStorageProperties storageProperties,
                              ActivityTimelineService activity, AuditService audit, OutboxService outbox, Clock clock) {
        this.assets = assets;
        this.requirements = requirements;
        this.versions = versions;
        this.reviews = reviews;
        this.stepCommands = stepCommands;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<AssetSummaryResponse> list(TenantPrincipal principal, AssetStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Asset> result = status == null ? assets.findAllByOrganizationId(principal.organizationId(), pageable)
                : assets.findAllByOrganizationIdAndStatus(principal.organizationId(), status, pageable);
        Set<UUID> requirementIds = result.getContent().stream().map(Asset::getRequirementId).collect(Collectors.toSet());
        Set<UUID> versionIds = result.getContent().stream().map(Asset::getCurrentVersionId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, AssetRequirement> requirementById = requirementIds.isEmpty() ? Map.of() : requirements
                .findAllByOrganizationIdAndIdIn(principal.organizationId(), requirementIds).stream()
                .collect(Collectors.toMap(AssetRequirement::getId, Function.identity()));
        Map<UUID, AssetVersion> versionById = versionIds.isEmpty() ? Map.of() : versions
                .findAllByOrganizationIdAndIdIn(principal.organizationId(), versionIds).stream()
                .collect(Collectors.toMap(AssetVersion::getId, Function.identity()));
        List<AssetSummaryResponse> items = result.getContent().stream().map(asset -> summary(
                asset, requirementById.get(asset.getRequirementId()), versionById.get(asset.getCurrentVersionId()))).toList();
        return new PageResult<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AssetDetailResponse detail(TenantPrincipal principal, UUID assetId) {
        Asset asset = assets.findByOrganizationIdAndId(principal.organizationId(), assetId).orElseThrow(AssetReviewService::notFound);
        AssetRequirement requirement = requireRequirement(principal.organizationId(), asset);
        List<AssetVersionResponse> versionResponses = versions
                .findAllByOrganizationIdAndAssetIdOrderByVersionNumberDesc(principal.organizationId(), assetId).stream()
                .map(AssetReviewService::versionResponse).toList();
        List<AssetReviewResponse> reviewResponses = reviews
                .findAllByOrganizationIdAndAssetIdOrderByCreatedAtDescIdDesc(principal.organizationId(), assetId).stream()
                .map(AssetReviewService::reviewResponse).toList();
        return new AssetDetailResponse(asset.getId(), asset.getProjectId(), asset.getOnboardingId(), asset.getStepInstanceId(),
                requirement.getFormSubmissionId(), requirement.getFormFieldId(), requirement.getName(), requirement.getDescription(),
                requirement.isRequired(), requirement.isRequiresReview(), requirement.getMaxFileSizeBytes(),
                AssetRequirementJson.mimeSet(requirement.getAllowedMimeTypes()).stream().sorted().toList(), asset.getStatus(), asset.getCurrentVersionId(),
                asset.getVersion(), asset.getUpdatedAt(), versionResponses, reviewResponses);
    }

    @Transactional
    public AssetSummaryResponse startReview(TenantPrincipal principal, UUID assetId, long expectedVersion, HttpServletRequest request) {
        Asset asset = requireForUpdate(principal.organizationId(), assetId);
        requireVersion(asset, expectedVersion);
        AssetVersion current = requireCleanCurrent(principal.organizationId(), asset);
        AssetRequirement requirement = requireRequirement(principal.organizationId(), asset);
        try { asset.startReview(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        assets.saveAndFlush(asset);
        reviews.save(new AssetReview(UUID.randomUUID(), principal.organizationId(), assetId, current.getId(), principal.userId(),
                AssetReviewAction.REVIEW_STARTED, null, clock.instant()));
        stepCommands.transition(principal.organizationId(), asset.getOnboardingId(), asset.getStepInstanceId(),
                OnboardingStepStatus.UNDER_REVIEW, principal.userId());
        record(principal, asset, "ASSET_REVIEW_STARTED", null, Map.of("status", asset.getStatus()), request);
        return summary(asset, requirement, current);
    }

    @Transactional
    public AssetSummaryResponse approve(TenantPrincipal principal, UUID assetId, long expectedVersion, HttpServletRequest request) {
        Asset asset = requireForUpdate(principal.organizationId(), assetId);
        requireVersion(asset, expectedVersion);
        AssetVersion current = requireCleanCurrent(principal.organizationId(), asset);
        AssetRequirement requirement = requireRequirement(principal.organizationId(), asset);
        try { asset.approve(principal.userId(), AssetActorType.INTERNAL, clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        assets.saveAndFlush(asset);
        reviews.save(new AssetReview(UUID.randomUUID(), principal.organizationId(), assetId, current.getId(), principal.userId(),
                AssetReviewAction.APPROVED, null, clock.instant()));
        stepCommands.transition(principal.organizationId(), asset.getOnboardingId(), asset.getStepInstanceId(),
                OnboardingStepStatus.COMPLETED, principal.userId());
        outbox.record(principal.organizationId(), "ASSET_APPROVED", "ASSET", assetId,
                Map.of("assetId", assetId, "assetVersionId", current.getId(), "projectId", asset.getProjectId()));
        record(principal, asset, "ASSET_APPROVED", null, Map.of("status", asset.getStatus()), request);
        return summary(asset, requirement, current);
    }

    @Transactional
    public AssetSummaryResponse requestRevision(TenantPrincipal principal, UUID assetId, long expectedVersion, String note,
                                                HttpServletRequest request) {
        Asset asset = requireForUpdate(principal.organizationId(), assetId);
        requireVersion(asset, expectedVersion);
        AssetVersion current = requireCleanCurrent(principal.organizationId(), asset);
        AssetRequirement requirement = requireRequirement(principal.organizationId(), asset);
        try { asset.requestRevision(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        assets.saveAndFlush(asset);
        reviews.save(new AssetReview(UUID.randomUUID(), principal.organizationId(), assetId, current.getId(), principal.userId(),
                AssetReviewAction.REVISION_REQUESTED, note, clock.instant()));
        stepCommands.transition(principal.organizationId(), asset.getOnboardingId(), asset.getStepInstanceId(),
                OnboardingStepStatus.NEEDS_REVISION, principal.userId());
        outbox.record(principal.organizationId(), "ASSET_REVISION_REQUESTED", "ASSET", assetId,
                Map.of("assetId", assetId, "assetVersionId", current.getId(), "projectId", asset.getProjectId()));
        record(principal, asset, "ASSET_REVISION_REQUESTED", null, Map.of("status", asset.getStatus(), "reason", note), request);
        return summary(asset, requirement, current);
    }

    @Transactional
    public AssetSummaryResponse retryScan(TenantPrincipal principal, UUID assetId, UUID assetVersionId, long expectedVersion,
                                          HttpServletRequest request) {
        Asset asset = requireForUpdate(principal.organizationId(), assetId);
        requireVersion(asset, expectedVersion);
        if (!assetVersionId.equals(asset.getCurrentVersionId()) || asset.getStatus() != AssetStatus.SCANNING) throw state("Only the current failed scan can be retried.");
        AssetVersion version = versions.findForUpdate(principal.organizationId(), assetVersionId).orElseThrow(AssetReviewService::notFound);
        try { version.resetScan(clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        versions.saveAndFlush(version);
        reviews.save(new AssetReview(UUID.randomUUID(), principal.organizationId(), assetId, assetVersionId, principal.userId(),
                AssetReviewAction.SCAN_RETRY_REQUESTED, null, clock.instant()));
        record(principal, asset, "ASSET_SCAN_RETRY_REQUESTED", null,
                Map.of("assetVersionId", assetVersionId, "status", version.getStatus()), request);
        return summary(asset, requireRequirement(principal.organizationId(), asset), version);
    }

    @Transactional(readOnly = true)
    public AssetDownloadUrlResponse download(TenantPrincipal principal, UUID assetId, UUID versionId) {
        Asset asset = assets.findByOrganizationIdAndId(principal.organizationId(), assetId).orElseThrow(AssetReviewService::notFound);
        AssetVersion version = versions.findByOrganizationIdAndId(principal.organizationId(), versionId).orElseThrow(AssetReviewService::notFound);
        if (!version.getAssetId().equals(assetId) || version.getStatus() != AssetVersionStatus.CLEAN) throw notFound();
        String mime = version.getDetectedMimeType() == null ? "application/octet-stream" : version.getDetectedMimeType();
        var signed = storage.presignDownload(version.getStorageBucket(), version.getObjectKey(), version.getSanitizedFilename(), mime,
                storageProperties.downloadUrlTtl());
        return new AssetDownloadUrlResponse(signed.url(), signed.expiresAt(), version.getSanitizedFilename(), mime);
    }

    private void record(TenantPrincipal principal, Asset asset, String action, Object before, Object after, HttpServletRequest request) {
        activity.record(principal.organizationId(), null, asset.getProjectId(), principal.userId(), action, "ASSET", asset.getId(),
                action.replace('_', ' ').toLowerCase(java.util.Locale.ROOT), Map.of("assetId", asset.getId()));
        audit.record(principal.organizationId(), principal.userId(), action, "ASSET", asset.getId(), before, after, request);
    }

    private Asset requireForUpdate(UUID organizationId, UUID assetId) {
        return assets.findForUpdate(organizationId, assetId).orElseThrow(AssetReviewService::notFound);
    }
    private AssetRequirement requireRequirement(UUID organizationId, Asset asset) {
        return requirements.findByOrganizationIdAndId(organizationId, asset.getRequirementId()).orElseThrow(AssetReviewService::notFound);
    }
    private AssetVersion requireCleanCurrent(UUID organizationId, Asset asset) {
        if (asset.getCurrentVersionId() == null) throw state("Asset has no uploaded version.");
        AssetVersion version = versions.findByOrganizationIdAndId(organizationId, asset.getCurrentVersionId()).orElseThrow(AssetReviewService::notFound);
        if (version.getStatus() != AssetVersionStatus.CLEAN) throw state("Asset cannot be reviewed until security scanning succeeds.");
        return version;
    }
    private static void requireVersion(Asset asset, long expected) {
        if (asset.getVersion() != expected) throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The asset changed. Refresh and try again.");
    }
    private static AssetVersionResponse versionResponse(AssetVersion v) {
        return new AssetVersionResponse(v.getId(), v.getVersionNumber(), v.getSanitizedFilename(), v.getExpectedSizeBytes(), v.getActualSizeBytes(),
                v.getDeclaredMimeType(), v.getDetectedMimeType(), v.getActualSha256(), v.getStatus(), v.getUploadedAt(), v.getScannedAt(),
                v.getScanAttemptCount(), v.getRejectionReason(), v.getCreatedAt(), v.getVersion());
    }

    private static AssetReviewResponse reviewResponse(AssetReview r) {
        return new AssetReviewResponse(r.getId(), r.getAssetVersionId(), r.getReviewerUserId(), r.getAction(), r.getNote(), r.getCreatedAt());
    }

    private static AssetSummaryResponse summary(Asset asset, AssetRequirement requirement, AssetVersion current) {
        return new AssetSummaryResponse(asset.getId(), asset.getProjectId(), asset.getStepInstanceId(),
                requirement == null ? "Asset" : requirement.getName(), asset.getStatus(), asset.getCurrentVersionId(),
                current == null ? null : current.getSanitizedFilename(), current == null ? null : current.getDetectedMimeType(),
                asset.getUpdatedAt(), asset.getVersion());
    }
    private static ApiException state(String message) { return new ApiException(HttpStatus.CONFLICT, "ASSET_STATE_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested asset was not found."); }

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
