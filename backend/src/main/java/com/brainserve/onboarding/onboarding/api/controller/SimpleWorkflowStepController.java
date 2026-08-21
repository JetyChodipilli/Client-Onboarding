package com.brainserve.onboarding.onboarding.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.api.response.SimpleStepActionResponse;
import com.brainserve.onboarding.onboarding.application.service.SimpleWorkflowStepService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SimpleWorkflowStepController {
    private final SimpleWorkflowStepService service;

    public SimpleWorkflowStepController(SimpleWorkflowStepService service) {
        this.service = service;
    }

    @PostMapping("/client-portal/projects/{projectId}/steps/{stepId}/complete")
    @PreAuthorize("hasAuthority('CLIENT_PORTAL')")
    ApiResponse<SimpleStepActionResponse> completeClient(@AuthenticationPrincipal ClientPrincipal principal,
                                                        @PathVariable UUID projectId,
                                                        @PathVariable UUID stepId) {
        return ApiResponse.success(service.completeClient(principal, projectId, stepId), RequestContext.requestId());
    }

    @PostMapping("/onboardings/{onboardingId}/steps/{stepId}/complete-manual")
    @PreAuthorize("hasAuthority('ONBOARDING_REVIEW')")
    ApiResponse<SimpleStepActionResponse> completeInternal(@AuthenticationPrincipal TenantPrincipal principal,
                                                          @PathVariable UUID onboardingId,
                                                          @PathVariable UUID stepId) {
        return ApiResponse.success(service.completeInternal(principal, onboardingId, stepId), RequestContext.requestId());
    }
}
