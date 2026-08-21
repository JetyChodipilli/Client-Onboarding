package com.brainserve.onboarding.integrations.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.integrations.api.response.IntegrationStatusResponse;
import com.brainserve.onboarding.integrations.application.service.IntegrationStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
@PreAuthorize("hasAnyAuthority('ORG_READ','ORG_UPDATE')")
public class IntegrationStatusController {
    private final IntegrationStatusService service;
    public IntegrationStatusController(IntegrationStatusService service) { this.service = service; }
    @GetMapping
    ApiResponse<IntegrationStatusResponse> status() {
        return ApiResponse.success(service.status(), RequestContext.requestId());
    }
}
