package com.brainserve.onboarding.access.api.controller;

import com.brainserve.onboarding.access.api.request.PlatformAccessReviewRequest;
import com.brainserve.onboarding.access.api.response.*;
import com.brainserve.onboarding.access.application.service.PlatformAccessService;
import com.brainserve.onboarding.access.domain.model.PlatformAccessRequestStatus;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform-access-requests")
public class PlatformAccessController {
    private final PlatformAccessService service;
    public PlatformAccessController(PlatformAccessService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAnyAuthority('ACCESS_READ','ACCESS_VERIFY','ACCESS_MANAGE')")
    ApiResponse<List<PlatformAccessQueueItemResponse>> list(@AuthenticationPrincipal TenantPrincipal p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(required=false)UUID projectId,@RequestParam(required=false)PlatformAccessRequestStatus status){var r=service.list(p,page,size,projectId,status);return ApiResponse.success(r.items(),Map.of("page",r.page(),"size",r.size(),"totalElements",r.totalElements(),"totalPages",r.totalPages()),RequestContext.requestId());}

    @GetMapping("/{id}") @PreAuthorize("hasAnyAuthority('ACCESS_READ','ACCESS_VERIFY','ACCESS_MANAGE')")
    ApiResponse<PlatformAccessRequestResponse> get(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id){return ApiResponse.success(service.get(p,id),RequestContext.requestId());}

    @PostMapping("/{id}/review") @PreAuthorize("hasAuthority('ACCESS_VERIFY')")
    ApiResponse<PlatformAccessRequestResponse> review(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@Valid @RequestBody PlatformAccessReviewRequest body,HttpServletRequest request){return ApiResponse.success(service.startVerification(p,id,body,request),RequestContext.requestId());}

    @PostMapping("/{id}/verify") @PreAuthorize("hasAuthority('ACCESS_VERIFY')")
    ApiResponse<PlatformAccessRequestResponse> verify(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@Valid @RequestBody PlatformAccessReviewRequest body,HttpServletRequest request){return ApiResponse.success(service.verify(p,id,body,request),RequestContext.requestId());}

    @PostMapping("/{id}/request-revision") @PreAuthorize("hasAuthority('ACCESS_VERIFY')")
    ApiResponse<PlatformAccessRequestResponse> revision(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@Valid @RequestBody PlatformAccessReviewRequest body,HttpServletRequest request){return ApiResponse.success(service.requestRevision(p,id,body,request),RequestContext.requestId());}

    @PostMapping("/{id}/waive") @PreAuthorize("hasAuthority('ACCESS_VERIFY')")
    ApiResponse<PlatformAccessRequestResponse> waive(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@Valid @RequestBody PlatformAccessReviewRequest body,HttpServletRequest request){return ApiResponse.success(service.waive(p,id,body,request),RequestContext.requestId());}
}
