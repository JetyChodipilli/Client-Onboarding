package com.brainserve.onboarding.contracts.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.contracts.api.request.*;
import com.brainserve.onboarding.contracts.api.response.*;
import com.brainserve.onboarding.contracts.application.service.ContractService;
import com.brainserve.onboarding.contracts.domain.model.ContractStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {
    private final ContractService service;
    public ContractController(ContractService service){this.service=service;}

    @PostMapping @PreAuthorize("hasAuthority('CONTRACT_CREATE')")
    ApiResponse<ContractDetailResponse> create(@AuthenticationPrincipal TenantPrincipal principal,
        @RequestHeader("Idempotency-Key") @Size(min=8,max=128) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
        @Valid @RequestBody CreateContractRequest body,HttpServletRequest request){return ApiResponse.success(service.create(principal,key,body,request),RequestContext.requestId());}

    @GetMapping @PreAuthorize("hasAnyAuthority('CONTRACT_CREATE','CONTRACT_SEND')")
    ApiResponse<List<ContractSummaryResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,@RequestParam(required=false)UUID projectId,@RequestParam(required=false)ContractStatus status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){var r=service.list(principal,projectId,status,page,size);return ApiResponse.success(r.items(),Map.of("page",r.page(),"size",r.size(),"totalElements",r.totalElements(),"totalPages",r.totalPages()),RequestContext.requestId());}

    @GetMapping("/{contractId}") @PreAuthorize("hasAnyAuthority('CONTRACT_CREATE','CONTRACT_SEND')")
    ApiResponse<ContractDetailResponse> detail(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID contractId){return ApiResponse.success(service.detail(principal,contractId),RequestContext.requestId());}

    @PostMapping("/{contractId}/send") @PreAuthorize("hasAuthority('CONTRACT_SEND')")
    ApiResponse<ContractDetailResponse> send(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID contractId,
        @RequestHeader("Idempotency-Key") @Size(min=8,max=128) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
        @Valid @RequestBody ContractVersionRequest body,HttpServletRequest request){return ApiResponse.success(service.send(principal,contractId,body.version(),key,request),RequestContext.requestId());}

    @PostMapping("/{contractId}/cancel") @PreAuthorize("hasAuthority('CONTRACT_SEND')")
    ApiResponse<ContractDetailResponse> cancel(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID contractId,@Valid @RequestBody ContractActionRequest body,HttpServletRequest request){return ApiResponse.success(service.cancel(principal,contractId,body.version(),body.reason(),request),RequestContext.requestId());}

    @PostMapping("/{contractId}/void") @PreAuthorize("hasAuthority('CONTRACT_SEND')")
    ApiResponse<ContractDetailResponse> voidContract(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID contractId,@Valid @RequestBody ContractActionRequest body,HttpServletRequest request){return ApiResponse.success(service.voidContract(principal,contractId,body.version(),body.reason(),request),RequestContext.requestId());}

    @GetMapping("/{contractId}/signed-document") @PreAuthorize("hasAnyAuthority('CONTRACT_CREATE','CONTRACT_SEND')")
    ApiResponse<ContractDownloadResponse> signedDocument(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID contractId){return ApiResponse.success(service.internalDownload(principal,contractId),RequestContext.requestId());}
}
