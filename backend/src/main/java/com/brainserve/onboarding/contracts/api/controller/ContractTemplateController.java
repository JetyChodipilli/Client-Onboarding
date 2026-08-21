package com.brainserve.onboarding.contracts.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.contracts.api.request.*;
import com.brainserve.onboarding.contracts.api.response.*;
import com.brainserve.onboarding.contracts.application.service.ContractTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contract-templates")
public class ContractTemplateController {
    private final ContractTemplateService service;
    public ContractTemplateController(ContractTemplateService service){this.service=service;}

    @PostMapping @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractTemplateResponse> create(@AuthenticationPrincipal TenantPrincipal principal,@Valid @RequestBody CreateContractTemplateRequest body,HttpServletRequest request){return ApiResponse.success(service.create(principal,body,request), RequestContext.requestId());}

    @GetMapping @PreAuthorize("hasAnyAuthority('CONTRACT_CREATE','CONTRACT_SEND')")
    ApiResponse<List<ContractTemplateResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){var r=service.list(principal,page,size);return ApiResponse.success(r.items(), Map.of("page",r.page(),"size",r.size(),"totalElements",r.totalElements(),"totalPages",r.totalPages()),RequestContext.requestId());}

    @GetMapping("/published-versions") @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<List<ContractTemplateVersionResponse>> published(@AuthenticationPrincipal TenantPrincipal principal){return ApiResponse.success(service.publishedVersions(principal),RequestContext.requestId());}

    @GetMapping("/{templateId}") @PreAuthorize("hasAnyAuthority('CONTRACT_CREATE','CONTRACT_SEND')")
    ApiResponse<ContractTemplateResponse> detail(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID templateId){return ApiResponse.success(service.detail(principal,templateId),RequestContext.requestId());}

    @PatchMapping("/{templateId}") @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractTemplateResponse> update(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID templateId,@Valid @RequestBody UpdateContractTemplateRequest body,HttpServletRequest request){return ApiResponse.success(service.update(principal,templateId,body,request),RequestContext.requestId());}

    @PostMapping("/{templateId}/archive") @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractTemplateResponse> archive(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID templateId,@Valid @RequestBody ContractVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.archive(principal,templateId,body.version(),request),RequestContext.requestId());}

    @PostMapping("/{templateId}/versions") @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractTemplateVersionResponse> createVersion(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID templateId,HttpServletRequest request){return ApiResponse.success(service.createVersion(principal,templateId,request),RequestContext.requestId());}

    @GetMapping("/versions/{versionId}") @PreAuthorize("hasAnyAuthority('CONTRACT_CREATE','CONTRACT_SEND')")
    ApiResponse<ContractTemplateVersionResponse> version(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID versionId){return ApiResponse.success(service.version(principal,versionId),RequestContext.requestId());}

    @PutMapping("/versions/{versionId}") @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractTemplateVersionResponse> updateVersion(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID versionId,@Valid @RequestBody UpdateContractTemplateVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.updateVersion(principal,versionId,body,request),RequestContext.requestId());}

    @PostMapping("/versions/{versionId}/publish") @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractTemplateVersionResponse> publish(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID versionId,@Valid @RequestBody ContractVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.publish(principal,versionId,body.version(),request),RequestContext.requestId());}
}
