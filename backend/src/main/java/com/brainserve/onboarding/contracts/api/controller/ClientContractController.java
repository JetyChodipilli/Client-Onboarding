package com.brainserve.onboarding.contracts.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.contracts.api.response.*;
import com.brainserve.onboarding.contracts.application.service.ContractService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/client-portal/projects/{projectId}/contracts")
@PreAuthorize("hasAuthority('CLIENT_PORTAL')")
public class ClientContractController {
    private final ContractService service;
    public ClientContractController(ContractService service){this.service=service;}

    @GetMapping
    ApiResponse<List<ClientContractSummaryResponse>> list(@AuthenticationPrincipal ClientPrincipal principal,@PathVariable UUID projectId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){var r=service.clientList(principal,projectId,page,size);return ApiResponse.success(r.items(),Map.of("page",r.page(),"size",r.size(),"totalElements",r.totalElements(),"totalPages",r.totalPages()),RequestContext.requestId());}

    @GetMapping("/{contractId}")
    ApiResponse<ClientContractDetailResponse> detail(@AuthenticationPrincipal ClientPrincipal principal,@PathVariable UUID projectId,@PathVariable UUID contractId){return ApiResponse.success(service.clientDetail(principal,projectId,contractId,false,null),RequestContext.requestId());}

    @PostMapping("/{contractId}/view")
    ApiResponse<ClientContractDetailResponse> view(@AuthenticationPrincipal ClientPrincipal principal,@PathVariable UUID projectId,@PathVariable UUID contractId,HttpServletRequest request){return ApiResponse.success(service.clientDetail(principal,projectId,contractId,true,request),RequestContext.requestId());}

    @GetMapping("/{contractId}/signed-document")
    ApiResponse<ContractDownloadResponse> signedDocument(@AuthenticationPrincipal ClientPrincipal principal,@PathVariable UUID projectId,@PathVariable UUID contractId){return ApiResponse.success(service.clientDownload(principal,projectId,contractId),RequestContext.requestId());}
}
