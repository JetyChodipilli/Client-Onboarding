package com.brainserve.onboarding.assets.api.controller;

import com.brainserve.onboarding.assets.api.request.CompleteAssetUploadRequest;
import com.brainserve.onboarding.assets.api.request.RequestAssetUploadRequest;
import com.brainserve.onboarding.assets.api.response.AssetDownloadUrlResponse;
import com.brainserve.onboarding.assets.api.response.AssetStepResponse;
import com.brainserve.onboarding.assets.api.response.AssetUploadUrlResponse;
import com.brainserve.onboarding.assets.application.service.ClientAssetService;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/client-portal/projects/{projectId}/assets")
@PreAuthorize("hasAuthority('CLIENT_PORTAL')")
public class ClientAssetController {
    private final ClientAssetService service;

    public ClientAssetController(ClientAssetService service) {
        this.service = service;
    }

    @GetMapping("/steps/{stepId}")
    ApiResponse<AssetStepResponse> get(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID stepId) {
        return ApiResponse.success(service.get(principal, projectId, stepId), RequestContext.requestId());
    }

    @PostMapping("/steps/{stepId}/upload-url")
    ApiResponse<AssetUploadUrlResponse> requestUpload(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID stepId,
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "Idempotency-Key contains unsupported characters") String idempotencyKey,
            @Valid @RequestBody RequestAssetUploadRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.requestUpload(principal, projectId, stepId, idempotencyKey, body, request), RequestContext.requestId());
    }

    @PostMapping("/steps/{stepId}/complete-upload")
    ApiResponse<AssetStepResponse> completeUpload(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID stepId,
            @Valid @RequestBody CompleteAssetUploadRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.completeUpload(principal, projectId, stepId, body, request), RequestContext.requestId());
    }

    @GetMapping("/{assetId}/versions/{versionId}/download-url")
    ApiResponse<AssetDownloadUrlResponse> download(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @PathVariable UUID versionId) {
        return ApiResponse.success(service.download(principal, projectId, assetId, versionId), RequestContext.requestId());
    }
}
