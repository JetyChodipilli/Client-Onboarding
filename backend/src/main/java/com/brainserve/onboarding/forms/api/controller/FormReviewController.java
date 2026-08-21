package com.brainserve.onboarding.forms.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.forms.api.request.RequestFormRevisionRequest;
import com.brainserve.onboarding.forms.api.request.ReviewFormSubmissionRequest;
import com.brainserve.onboarding.forms.api.response.FormReviewQueueItemResponse;
import com.brainserve.onboarding.forms.api.response.FormSubmissionResponse;
import com.brainserve.onboarding.forms.application.service.FormSubmissionService;
import com.brainserve.onboarding.forms.domain.model.FormSubmissionStatus;
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
@RequestMapping("/api/v1/form-submissions")
public class FormReviewController {
    private final FormSubmissionService service;
    public FormReviewController(FormSubmissionService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('FORM_REVIEW') or hasAuthority('FORM_READ')")
    ApiResponse<List<FormReviewQueueItemResponse>> queue(@AuthenticationPrincipal TenantPrincipal principal,
                                                         @RequestParam(required = false) FormSubmissionStatus status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        var result = service.reviewQueue(principal, status, page, size);
        return ApiResponse.success(result.items(), Map.of("page", result.page(), "size", result.size(), "totalElements", result.totalElements(), "totalPages", result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{submissionId}")
    @PreAuthorize("hasAuthority('FORM_REVIEW') or hasAuthority('FORM_READ')")
    ApiResponse<FormSubmissionResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID submissionId) {
        return ApiResponse.success(service.getSubmission(principal, submissionId), RequestContext.requestId());
    }

    @GetMapping("/steps/{stepId}/history")
    @PreAuthorize("hasAuthority('FORM_REVIEW') or hasAuthority('FORM_READ')")
    ApiResponse<List<FormSubmissionResponse>> history(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID stepId) {
        return ApiResponse.success(service.history(principal, stepId), RequestContext.requestId());
    }

    @PostMapping("/{submissionId}/review")
    @PreAuthorize("hasAuthority('FORM_REVIEW')")
    ApiResponse<FormSubmissionResponse> review(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID submissionId,
                                               @Valid @RequestBody ReviewFormSubmissionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.startReview(principal, submissionId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{submissionId}/approve")
    @PreAuthorize("hasAuthority('FORM_REVIEW')")
    ApiResponse<FormSubmissionResponse> approve(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID submissionId,
                                                @Valid @RequestBody ReviewFormSubmissionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.approve(principal, submissionId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{submissionId}/request-revision")
    @PreAuthorize("hasAuthority('FORM_REVIEW')")
    ApiResponse<FormSubmissionResponse> requestRevision(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID submissionId,
                                                        @Valid @RequestBody RequestFormRevisionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.requestRevision(principal, submissionId, body, request), RequestContext.requestId());
    }
}
