package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.assets.domain.model.Asset;
import com.brainserve.onboarding.assets.domain.model.AssetActorType;
import com.brainserve.onboarding.assets.domain.model.AssetRequirement;
import com.brainserve.onboarding.assets.domain.model.AssetVersion;
import com.brainserve.onboarding.assets.domain.model.AssetVersionStatus;
import com.brainserve.onboarding.assets.infrastructure.config.AssetScanProperties;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRequirementRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetVersionRepository;
import com.brainserve.onboarding.assets.infrastructure.scanning.MalwareScanner;
import com.brainserve.onboarding.assets.infrastructure.storage.AssetObjectStorage;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.application.service.WorkflowActorType;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background security pipeline. Claiming/finalization are short transactions; object download and malware
 * scanning happen outside a database transaction so slow storage or scanners cannot hold row locks.
 */
@Service
public class AssetScanService {
    private static final Logger log = LoggerFactory.getLogger(AssetScanService.class);
    private static final Set<AssetVersionStatus> QUEUED = Set.of(AssetVersionStatus.SCAN_PENDING, AssetVersionStatus.SCAN_FAILED);

    private final AssetVersionRepository versions;
    private final AssetRepository assets;
    private final AssetRequirementRepository requirements;
    private final AssetObjectStorage storage;
    private final MalwareScanner scanner;
    private final AssetContentInspector inspector;
    private final AssetPolicy policy;
    private final AssetScanProperties scanProperties;
    private final OnboardingStepCommandService stepCommands;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public AssetScanService(AssetVersionRepository versions, AssetRepository assets, AssetRequirementRepository requirements,
                            AssetObjectStorage storage, MalwareScanner scanner, AssetContentInspector inspector,
                            AssetPolicy policy, AssetScanProperties scanProperties, OnboardingStepCommandService stepCommands,
                            AuditService audit, OutboxService outbox, Clock clock, TransactionTemplate transactions) {
        this.versions = versions;
        this.assets = assets;
        this.requirements = requirements;
        this.storage = storage;
        this.scanner = scanner;
        this.inspector = inspector;
        this.policy = policy;
        this.scanProperties = scanProperties;
        this.stepCommands = stepCommands;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${app.assets.scan.poll-delay:10s}")
    public void processQueue() {
        if (!scanProperties.enabled() || !scanner.available() || !storage.available()) return;
        Instant now = clock.instant();
        List<AssetVersion> candidates = versions.findScanCandidates(QUEUED, now, PageRequest.of(0, scanProperties.batchSize()));
        for (AssetVersion candidate : candidates) processOne(candidate.getOrganizationId(), candidate.getId());
    }

    public void processOne(UUID organizationId, UUID versionId) {
        Claim claim = transactions.execute(status -> claim(organizationId, versionId));
        if (claim == null) return;
        Path temp = null;
        try {
            temp = Files.createTempFile("asset-scan-", ".bin");
            storage.downloadTo(claim.bucket(), claim.objectKey(), temp);
            AssetContentInspector.Inspection inspection = inspector.inspect(temp);
            try {
                policy.validateDetectedFile(claim.policy(), claim.declaredMimeType(), inspection.detectedMimeType(),
                        inspection.sizeBytes(), claim.expectedSha256(), inspection.sha256());
            } catch (RuntimeException invalid) {
                finalizeRejected(claim, inspection, safeMessage(invalid));
                return;
            }
            MalwareScanner.ScanResult result = scanner.scan(temp);
            if (!result.clean()) {
                finalizeQuarantined(claim, inspection, result.signature());
                return;
            }
            finalizeClean(claim, inspection);
        } catch (RuntimeException | IOException ex) {
            log.warn("Asset scan failed for version {}: {}", versionId, ex.getMessage());
            finalizeFailure(claim, ex);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ex) { log.warn("Could not delete asset scan temp file {}", temp); }
            }
        }
    }

    private Claim claim(UUID organizationId, UUID versionId) {
        AssetVersion locked = versions.findForUpdate(organizationId, versionId).orElse(null);
        if (locked == null || !QUEUED.contains(locked.getStatus())) return null;
        Instant now = clock.instant();
        if (locked.getNextScanAt() != null && locked.getNextScanAt().isAfter(now)) return null;
        if (locked.getScanAttemptCount() >= scanProperties.maxAttempts()) return null;

        Asset asset = assets.findForUpdate(locked.getOrganizationId(), locked.getAssetId()).orElseThrow();
        AssetRequirement requirement = requirements.findByOrganizationIdAndId(locked.getOrganizationId(), locked.getRequirementId()).orElseThrow();
        locked.scanStarted(now);
        if (asset.getStatus() != com.brainserve.onboarding.assets.domain.model.AssetStatus.SCANNING) asset.scanStarted(now);
        versions.saveAndFlush(locked);
        assets.saveAndFlush(asset);
        return new Claim(locked.getOrganizationId(), locked.getId(), locked.getAssetId(), locked.getProjectId(), asset.getOnboardingId(),
                asset.getStepInstanceId(), requirement.getFormSubmissionId() != null, requirement.isRequiresReview(), locked.getCreatedBy(), locked.getStorageBucket(), locked.getObjectKey(),
                locked.getDeclaredMimeType(), locked.getExpectedSha256(), requirementPolicy(requirement));
    }

    private void finalizeClean(Claim claim, AssetContentInspector.Inspection inspection) {
        transactions.executeWithoutResult(status -> {
            AssetVersion version = requireScanning(claim);
            Asset asset = assets.findForUpdate(claim.organizationId(), claim.assetId()).orElseThrow();
            Instant now = clock.instant();
            version.clean(inspection.detectedMimeType(), inspection.sha256(), now);
            asset.clean(null, AssetActorType.SYSTEM, now);
            versions.saveAndFlush(version);
            assets.saveAndFlush(asset);

            if (claim.formAttachment()) {
                // A questionnaire owns its own submit/review lifecycle. Scanning only establishes a safe opaque file reference.
                outbox.record(claim.organizationId(), "ASSET_SCAN_CLEAN", "ASSET", claim.assetId(),
                        Map.of("assetId", claim.assetId(), "assetVersionId", claim.versionId(), "projectId", claim.projectId(), "formAttachment", true));
            } else {
                stepCommands.transition(claim.organizationId(), claim.onboardingId(), claim.stepId(), OnboardingStepStatus.SUBMITTED,
                        claim.uploaderUserId(), WorkflowActorType.CLIENT);
                if (!claim.requiresReview()) {
                    asset.approve(null, AssetActorType.SYSTEM, now);
                    assets.saveAndFlush(asset);
                    stepCommands.transition(claim.organizationId(), claim.onboardingId(), claim.stepId(), OnboardingStepStatus.COMPLETED,
                            claim.uploaderUserId(), WorkflowActorType.CLIENT);
                    outbox.record(claim.organizationId(), "ASSET_APPROVED", "ASSET", claim.assetId(),
                            Map.of("assetId", claim.assetId(), "assetVersionId", claim.versionId(), "projectId", claim.projectId()));
                }
            }
            audit.recordApplication(claim.organizationId(), null, "SYSTEM", "ASSET_SCAN_CLEAN", "ASSET", claim.assetId(), null,
                    Map.of("assetVersionId", claim.versionId(), "detectedMimeType", inspection.detectedMimeType(), "sha256", inspection.sha256()));
        });
    }

    private void finalizeQuarantined(Claim claim, AssetContentInspector.Inspection inspection, String signature) {
        transactions.executeWithoutResult(status -> {
            AssetVersion version = requireScanning(claim);
            Asset asset = assets.findForUpdate(claim.organizationId(), claim.assetId()).orElseThrow();
            Instant now = clock.instant();
            version.quarantined(signature, inspection.detectedMimeType(), inspection.sha256(), now);
            asset.quarantine(now);
            versions.saveAndFlush(version);
            assets.saveAndFlush(asset);
            if (!claim.formAttachment()) failWorkflowStep(claim);
            outbox.record(claim.organizationId(), "ASSET_QUARANTINED", "ASSET", claim.assetId(),
                    Map.of("assetId", claim.assetId(), "assetVersionId", claim.versionId(), "projectId", claim.projectId()));
            audit.recordApplication(claim.organizationId(), null, "SYSTEM", "ASSET_QUARANTINED", "ASSET", claim.assetId(), null,
                    Map.of("assetVersionId", claim.versionId(), "reason", "Malware detected"));
        });
    }

    private void finalizeRejected(Claim claim, AssetContentInspector.Inspection inspection, String reason) {
        transactions.executeWithoutResult(status -> {
            AssetVersion version = requireScanning(claim);
            Asset asset = assets.findForUpdate(claim.organizationId(), claim.assetId()).orElseThrow();
            Instant now = clock.instant();
            version.rejected(reason, inspection.detectedMimeType(), inspection.sha256(), now);
            asset.reject(null, AssetActorType.SYSTEM, now);
            versions.saveAndFlush(version);
            assets.saveAndFlush(asset);
            if (!claim.formAttachment()) failWorkflowStep(claim);
            audit.recordApplication(claim.organizationId(), null, "SYSTEM", "ASSET_REJECTED", "ASSET", claim.assetId(), null,
                    Map.of("assetVersionId", claim.versionId(), "reason", reason));
        });
    }

    private void finalizeFailure(Claim claim, Exception ex) {
        transactions.executeWithoutResult(status -> {
            AssetVersion version = requireScanning(claim);
            int attempt = version.getScanAttemptCount();
            Duration delay = attempt >= scanProperties.maxAttempts() ? Duration.ofDays(36500) : retryDelay(attempt);
            version.scanFailed("Security scanning failed; the file remains unavailable.", delay, clock.instant());
            versions.saveAndFlush(version);
            audit.recordApplication(claim.organizationId(), null, "SYSTEM", "ASSET_SCAN_FAILED", "ASSET", claim.assetId(), null,
                    Map.of("assetVersionId", claim.versionId(), "attempt", attempt, "retryScheduled", attempt < scanProperties.maxAttempts()));
        });
    }

    private void failWorkflowStep(Claim claim) {
        stepCommands.transition(claim.organizationId(), claim.onboardingId(), claim.stepId(), OnboardingStepStatus.FAILED,
                claim.uploaderUserId(), WorkflowActorType.CLIENT);
    }

    private AssetVersion requireScanning(Claim claim) {
        AssetVersion version = versions.findForUpdate(claim.organizationId(), claim.versionId()).orElseThrow();
        if (version.getStatus() != AssetVersionStatus.SCANNING) throw new IllegalStateException("Asset version is no longer being scanned");
        return version;
    }

    private AssetPolicy.Policy requirementPolicy(AssetRequirement requirement) {
        return new AssetPolicy.Policy(requirement.getMaxFileSizeBytes(), AssetRequirementJson.mimeSet(requirement.getAllowedMimeTypes()),
                requirement.getAllowedMimeTypes());
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 7);
        Duration value = scanProperties.retryBaseDelay().multipliedBy(multiplier);
        return value.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : value;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "File content failed security validation." : message;
    }

    private record Claim(UUID organizationId, UUID versionId, UUID assetId, UUID projectId, UUID onboardingId,
                         UUID stepId, boolean formAttachment, boolean requiresReview, UUID uploaderUserId, String bucket, String objectKey,
                         String declaredMimeType, String expectedSha256, AssetPolicy.Policy policy) {}
}
