package com.brainserve.onboarding.assets.application.service;

import com.brainserve.onboarding.assets.domain.model.Asset;
import com.brainserve.onboarding.assets.domain.model.AssetReview;
import com.brainserve.onboarding.assets.domain.model.AssetReviewAction;
import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetRequirementRepository;
import com.brainserve.onboarding.assets.infrastructure.persistence.AssetReviewRepository;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.onboarding.application.service.FinalReviewRevisionHandler;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AssetFinalReviewRevisionHandler implements FinalReviewRevisionHandler {
    private final AssetRequirementRepository requirements;
    private final AssetRepository assets;
    private final AssetReviewRepository reviews;
    private final OutboxService outbox;

    public AssetFinalReviewRevisionHandler(AssetRequirementRepository requirements, AssetRepository assets,
                                           AssetReviewRepository reviews, OutboxService outbox) {
        this.requirements = requirements;
        this.assets = assets;
        this.reviews = reviews;
        this.outbox = outbox;
    }

    @Override public boolean supports(WorkflowStepType stepType) { return stepType == WorkflowStepType.FILE_UPLOAD; }

    @Override
    public void reopen(RevisionContext context) {
        var requirement = requirements.findByOrganizationIdAndStepInstanceIdAndRequirementKey(
                        context.organizationId(), context.stepId(), AssetRequirementService.WORKFLOW_REQUIREMENT_KEY)
                .orElseThrow(() -> new IllegalStateException("The asset requirement is missing."));
        Asset asset = assets.findForUpdateByRequirement(context.organizationId(), requirement.getId())
                .filter(value -> value.getStatus() == AssetStatus.APPROVED)
                .orElseThrow(() -> new IllegalStateException("The asset is not approved and cannot be reopened from final review."));
        asset.requestRevision(context.reviewerUserId(), context.occurredAt());
        assets.saveAndFlush(asset);
        reviews.saveAndFlush(new AssetReview(UUID.randomUUID(), context.organizationId(), asset.getId(),
                asset.getCurrentVersionId(), context.reviewerUserId(), AssetReviewAction.REVISION_REQUESTED,
                context.reason(), context.occurredAt()));
        outbox.record(context.organizationId(), "ASSET_REVISION_REQUESTED", "ASSET", asset.getId(),
                Map.of("assetId", asset.getId(), "stepId", context.stepId(), "projectId", context.projectId(),
                        "source", "FINAL_REVIEW"));
    }
}
