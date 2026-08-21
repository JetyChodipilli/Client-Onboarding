package com.brainserve.onboarding.audit.api.controller;

import com.brainserve.onboarding.audit.api.response.AuditLogResponse;
import com.brainserve.onboarding.audit.application.service.AuditQueryService;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    ApiResponse<List<AuditLogResponse>> list(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = service.list(principal.organizationId(), page, size);
        return ApiResponse.success(result.items(), Map.of(
                "page", result.page(), "size", result.size(),
                "totalElements", result.totalElements(), "totalPages", result.totalPages()),
                RequestContext.requestId());
    }
}
