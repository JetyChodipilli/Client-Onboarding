package com.brainserve.onboarding.assets.api.controller;

import com.brainserve.onboarding.assets.api.request.AssetReviewRequest;
import com.brainserve.onboarding.assets.api.request.AssetVersionRequest;
import com.brainserve.onboarding.assets.api.response.AssetDetailResponse;
import com.brainserve.onboarding.assets.api.response.AssetDownloadUrlResponse;
import com.brainserve.onboarding.assets.api.response.AssetSummaryResponse;
import com.brainserve.onboarding.assets.application.service.AssetReviewService;
import com.brainserve.onboarding.assets.domain.model.AssetStatus;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetReviewController {
    private final AssetReviewService service;

    public AssetReviewController(AssetReviewService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ASSET_READ') or hasAuthority('ASSET_REVIEW')")
    ApiResponse<List<AssetSummaryResponse>> list(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = service.list(principal, status, page, size);
        return ApiResponse.success(result.items(), Map.of(
                "page", result.page(),
                "size", result.size(),
                "totalElements", result.totalElements(),
                "totalPages", result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("hasAuthority('ASSET_READ') or hasAuthority('ASSET_REVIEW')")
    ApiResponse<AssetDetailResponse> detail(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID assetId) {
        return ApiResponse.success(service.detail(principal, assetId), RequestContext.requestId());
    }

    @PostMapping("/{assetId}/review")
    @PreAuthorize("hasAuthority('ASSET_REVIEW')")
    ApiResponse<AssetSummaryResponse> startReview(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetVersionRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.startReview(principal, assetId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{assetId}/approve")
    @PreAuthorize("hasAuthority('ASSET_REVIEW')")
    ApiResponse<AssetSummaryResponse> approve(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetVersionRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.approve(principal, assetId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{assetId}/request-revision")
    @PreAuthorize("hasAuthority('ASSET_REVIEW')")
    ApiResponse<AssetSummaryResponse> requestRevision(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetReviewRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.requestRevision(principal, assetId, body.version(), body.note(), request), RequestContext.requestId());
    }

    @PostMapping("/{assetId}/versions/{versionId}/retry-scan")
    @PreAuthorize("hasAuthority('ASSET_REVIEW')")
    ApiResponse<AssetSummaryResponse> retryScan(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID assetId,
            @PathVariable UUID versionId,
            @Valid @RequestBody AssetVersionRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.retryScan(principal, assetId, versionId, body.version(), request), RequestContext.requestId());
    }

    @GetMapping("/{assetId}/versions/{versionId}/download-url")
    @PreAuthorize("hasAuthority('ASSET_READ') or hasAuthority('ASSET_REVIEW')")
    ApiResponse<AssetDownloadUrlResponse> download(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID assetId,
            @PathVariable UUID versionId) {
        return ApiResponse.success(service.download(principal, assetId, versionId), RequestContext.requestId());
    }
}
