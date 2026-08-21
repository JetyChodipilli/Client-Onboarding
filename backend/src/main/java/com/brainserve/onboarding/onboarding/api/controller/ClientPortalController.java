package com.brainserve.onboarding.onboarding.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalDashboardResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalProjectDetailResponse;
import com.brainserve.onboarding.onboarding.application.service.ClientPortalService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-portal")
@PreAuthorize("hasAuthority('CLIENT_PORTAL')")
public class ClientPortalController {
    private final ClientPortalService portal;
    public ClientPortalController(ClientPortalService portal){this.portal=portal;}

    @GetMapping("/dashboard")
    ApiResponse<ClientPortalDashboardResponse> dashboard(@AuthenticationPrincipal ClientPrincipal principal){
        return ApiResponse.success(portal.dashboard(principal),RequestContext.requestId());
    }

    @GetMapping("/projects/{projectId}")
    ApiResponse<ClientPortalProjectDetailResponse> project(@AuthenticationPrincipal ClientPrincipal principal,@PathVariable UUID projectId){
        return ApiResponse.success(portal.project(principal,projectId),RequestContext.requestId());
    }
}
