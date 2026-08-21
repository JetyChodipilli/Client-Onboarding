package com.brainserve.onboarding.servicecatalog.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.servicecatalog.api.request.ArchiveServiceRequest;
import com.brainserve.onboarding.servicecatalog.api.request.CreateServiceRequest;
import com.brainserve.onboarding.servicecatalog.api.request.UpdateServiceRequest;
import com.brainserve.onboarding.servicecatalog.api.response.ServiceResponse;
import com.brainserve.onboarding.servicecatalog.application.service.ServiceCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('SERVICE_MANAGE')")
    ApiResponse<ServiceResponse> create(@AuthenticationPrincipal TenantPrincipal principal,
                                        @Valid @RequestBody CreateServiceRequest body,
                                        HttpServletRequest request) {
        return ApiResponse.success(service.create(principal, body, request), RequestContext.requestId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SERVICE_READ') or hasAuthority('SERVICE_MANAGE')")
    ApiResponse<List<ServiceResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
                                            @RequestParam(required = false) @Size(max = 120) String query,
                                            @RequestParam(defaultValue = "false") boolean includeArchived) {
        var result = service.list(principal, page, size, query, includeArchived);
        return ApiResponse.success(result.items(), Map.of("page", result.page(), "size", result.size(),
                "totalElements", result.totalElements(), "totalPages", result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize("hasAuthority('SERVICE_READ') or hasAuthority('SERVICE_MANAGE')")
    ApiResponse<ServiceResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID serviceId) {
        return ApiResponse.success(service.get(principal, serviceId), RequestContext.requestId());
    }

    @PatchMapping("/{serviceId}")
    @PreAuthorize("hasAuthority('SERVICE_MANAGE')")
    ApiResponse<ServiceResponse> update(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID serviceId,
                                        @Valid @RequestBody UpdateServiceRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(principal, serviceId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{serviceId}/archive")
    @PreAuthorize("hasAuthority('SERVICE_MANAGE')")
    ApiResponse<ServiceResponse> archive(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID serviceId,
                                         @Valid @RequestBody ArchiveServiceRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.archive(principal, serviceId, body, request), RequestContext.requestId());
    }
}
