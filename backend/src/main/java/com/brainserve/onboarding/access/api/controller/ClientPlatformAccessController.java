package com.brainserve.onboarding.access.api.controller;

import com.brainserve.onboarding.access.api.request.SubmitPlatformAccessRequest;
import com.brainserve.onboarding.access.api.response.PlatformAccessRequestResponse;
import com.brainserve.onboarding.access.application.service.PlatformAccessService;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/client-portal/projects/{projectId}/platform-access")
@PreAuthorize("hasAuthority('CLIENT_PORTAL')")
public class ClientPlatformAccessController {
    private final PlatformAccessService service;
    public ClientPlatformAccessController(PlatformAccessService service){this.service=service;}
    @GetMapping("/{stepId}")
    ApiResponse<PlatformAccessRequestResponse> get(@AuthenticationPrincipal ClientPrincipal p,@PathVariable UUID projectId,@PathVariable UUID stepId){return ApiResponse.success(service.clientGet(p,projectId,stepId),RequestContext.requestId());}
    @PostMapping("/{stepId}/submit")
    ApiResponse<PlatformAccessRequestResponse> submit(@AuthenticationPrincipal ClientPrincipal p,@PathVariable UUID projectId,@PathVariable UUID stepId,@Valid @RequestBody SubmitPlatformAccessRequest body,HttpServletRequest request){return ApiResponse.success(service.clientSubmit(p,projectId,stepId,body,request),RequestContext.requestId());}
}
