package com.brainserve.onboarding.onboarding.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.api.request.OnboardingReviewVersionRequest;
import com.brainserve.onboarding.onboarding.api.request.RequestOnboardingRevisionRequest;
import com.brainserve.onboarding.onboarding.api.request.StartOnboardingRequest;
import com.brainserve.onboarding.onboarding.api.response.FinalReviewChecklistResponse;
import com.brainserve.onboarding.onboarding.api.response.OnboardingResponse;
import com.brainserve.onboarding.onboarding.api.response.OnboardingStepResponse;
import com.brainserve.onboarding.onboarding.application.service.OnboardingFinalReviewService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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
@RequestMapping("/api/v1")
public class OnboardingController {
    private final OnboardingService service;
    private final OnboardingFinalReviewService finalReview;

    public OnboardingController(OnboardingService service, OnboardingFinalReviewService finalReview) {
        this.service = service;
        this.finalReview = finalReview;
    }

    @PostMapping("/projects/{projectId}/onboarding")
    @PreAuthorize("hasAuthority('ONBOARDING_START')")
    ApiResponse<OnboardingResponse> start(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID projectId,
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "Idempotency-Key contains unsupported characters") String idempotencyKey,
            @Valid @RequestBody StartOnboardingRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.start(principal, projectId, idempotencyKey, body, request), RequestContext.requestId());
    }

    @GetMapping("/projects/{projectId}/onboarding")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    ApiResponse<OnboardingResponse> byProject(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId) {
        return ApiResponse.success(service.getByProject(principal, projectId), RequestContext.requestId());
    }

    @GetMapping("/onboardings/{onboardingId}")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    ApiResponse<OnboardingResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID onboardingId) {
        return ApiResponse.success(service.get(principal, onboardingId), RequestContext.requestId());
    }


    @GetMapping("/projects/{projectId}/review")
    @PreAuthorize("hasAnyAuthority('ONBOARDING_REVIEW','ONBOARDING_APPROVE','PROJECT_ACTIVATE')")
    ApiResponse<FinalReviewChecklistResponse> projectReviewChecklist(@AuthenticationPrincipal TenantPrincipal principal,
                                                                      @PathVariable UUID projectId) {
        return ApiResponse.success(finalReview.checklistByProject(principal, projectId), RequestContext.requestId());
    }

    @GetMapping("/onboardings/{onboardingId}/review")
    @PreAuthorize("hasAnyAuthority('ONBOARDING_REVIEW','ONBOARDING_APPROVE','PROJECT_ACTIVATE')")
    ApiResponse<FinalReviewChecklistResponse> reviewChecklist(@AuthenticationPrincipal TenantPrincipal principal,
                                                               @PathVariable UUID onboardingId) {
        return ApiResponse.success(finalReview.checklist(principal, onboardingId), RequestContext.requestId());
    }

    @PostMapping("/onboardings/{onboardingId}/review")
    @PreAuthorize("hasAuthority('ONBOARDING_REVIEW')")
    ApiResponse<FinalReviewChecklistResponse> startReview(@AuthenticationPrincipal TenantPrincipal principal,
                                                           @PathVariable UUID onboardingId,
                                                           @Valid @RequestBody OnboardingReviewVersionRequest body,
                                                           HttpServletRequest request) {
        return ApiResponse.success(finalReview.startReview(principal, onboardingId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/onboardings/{onboardingId}/approve")
    @PreAuthorize("hasAuthority('ONBOARDING_APPROVE')")
    ApiResponse<FinalReviewChecklistResponse> approve(@AuthenticationPrincipal TenantPrincipal principal,
                                                       @PathVariable UUID onboardingId,
                                                       @Valid @RequestBody OnboardingReviewVersionRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success(finalReview.approve(principal, onboardingId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/onboardings/{onboardingId}/request-revision")
    @PreAuthorize("hasAuthority('ONBOARDING_REVIEW')")
    ApiResponse<FinalReviewChecklistResponse> requestRevision(@AuthenticationPrincipal TenantPrincipal principal,
                                                               @PathVariable UUID onboardingId,
                                                               @Valid @RequestBody RequestOnboardingRevisionRequest body,
                                                               HttpServletRequest request) {
        return ApiResponse.success(finalReview.requestRevision(principal, onboardingId, body, request), RequestContext.requestId());
    }

    @GetMapping("/onboardings/{onboardingId}/steps")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    ApiResponse<List<OnboardingStepResponse>> steps(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID onboardingId) {
        return ApiResponse.success(service.steps(principal, onboardingId), RequestContext.requestId());
    }
}
