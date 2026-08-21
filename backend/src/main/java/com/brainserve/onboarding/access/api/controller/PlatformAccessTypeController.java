package com.brainserve.onboarding.access.api.controller;

import com.brainserve.onboarding.access.api.request.*;
import com.brainserve.onboarding.access.api.response.*;
import com.brainserve.onboarding.access.application.service.PlatformAccessCatalogService;
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
@RequestMapping("/api/v1/platform-access-types")
public class PlatformAccessTypeController {
    private final PlatformAccessCatalogService service;
    public PlatformAccessTypeController(PlatformAccessCatalogService service){this.service=service;}

    @PostMapping @PreAuthorize("hasAuthority('ACCESS_MANAGE')")
    ApiResponse<PlatformAccessTypeDetailResponse> create(@AuthenticationPrincipal TenantPrincipal p,@Valid @RequestBody CreatePlatformAccessTypeRequest body,HttpServletRequest request){return ApiResponse.success(service.create(p,body,request),RequestContext.requestId());}
    @GetMapping @PreAuthorize("hasAnyAuthority('ACCESS_READ','ACCESS_MANAGE','ACCESS_VERIFY')")
    ApiResponse<List<PlatformAccessTypeSummaryResponse>> list(@AuthenticationPrincipal TenantPrincipal p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@RequestParam(defaultValue="false")boolean includeArchived){var r=service.list(p,page,size,includeArchived);return ApiResponse.success(r.items(),Map.of("page",r.page(),"size",r.size(),"totalElements",r.totalElements(),"totalPages",r.totalPages()),RequestContext.requestId());}
    @GetMapping("/published-versions") @PreAuthorize("hasAnyAuthority('ACCESS_READ','ACCESS_MANAGE')")
    ApiResponse<List<PublishedPlatformAccessGuideResponse>> published(@AuthenticationPrincipal TenantPrincipal p){return ApiResponse.success(service.publishedVersions(p),RequestContext.requestId());}
    @GetMapping("/{id}") @PreAuthorize("hasAnyAuthority('ACCESS_READ','ACCESS_MANAGE','ACCESS_VERIFY')")
    ApiResponse<PlatformAccessTypeDetailResponse> get(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id){return ApiResponse.success(service.get(p,id),RequestContext.requestId());}
    @PatchMapping("/{id}") @PreAuthorize("hasAuthority('ACCESS_MANAGE')")
    ApiResponse<PlatformAccessTypeDetailResponse> update(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@Valid @RequestBody UpdatePlatformAccessTypeRequest body,HttpServletRequest request){return ApiResponse.success(service.update(p,id,body,request),RequestContext.requestId());}
    @PostMapping("/{id}/archive") @PreAuthorize("hasAuthority('ACCESS_MANAGE')")
    ApiResponse<PlatformAccessTypeDetailResponse> archive(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@RequestBody ResourceVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.archive(p,id,body.version(),request),RequestContext.requestId());}
    @PostMapping("/{id}/versions") @PreAuthorize("hasAuthority('ACCESS_MANAGE')")
    ApiResponse<PlatformAccessGuideResponse> createVersion(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID id,@Valid @RequestBody CreatePlatformAccessGuideVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.createVersion(p,id,body,request),RequestContext.requestId());}
    @GetMapping("/versions/{versionId}") @PreAuthorize("hasAnyAuthority('ACCESS_READ','ACCESS_MANAGE','ACCESS_VERIFY')")
    ApiResponse<PlatformAccessGuideResponse> version(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID versionId){return ApiResponse.success(service.getVersion(p,versionId),RequestContext.requestId());}
    @PutMapping("/versions/{versionId}") @PreAuthorize("hasAuthority('ACCESS_MANAGE')")
    ApiResponse<PlatformAccessGuideResponse> save(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID versionId,@Valid @RequestBody SavePlatformAccessGuideRequest body,HttpServletRequest request){return ApiResponse.success(service.saveDraft(p,versionId,body,request),RequestContext.requestId());}
    @PostMapping("/versions/{versionId}/publish") @PreAuthorize("hasAuthority('ACCESS_MANAGE')")
    ApiResponse<PlatformAccessGuideResponse> publish(@AuthenticationPrincipal TenantPrincipal p,@PathVariable UUID versionId,@RequestBody ResourceVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.publish(p,versionId,body.version(),request),RequestContext.requestId());}
}
