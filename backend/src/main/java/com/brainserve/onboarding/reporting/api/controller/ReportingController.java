package com.brainserve.onboarding.reporting.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.reporting.api.response.OnboardingReportRow;
import com.brainserve.onboarding.reporting.api.response.ReportingSnapshotResponse;
import com.brainserve.onboarding.reporting.application.service.ReportingService;
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
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('REPORT_READ')")
public class ReportingController {
    private final ReportingService service;

    public ReportingController(ReportingService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    ApiResponse<ReportingSnapshotResponse> snapshot(@AuthenticationPrincipal TenantPrincipal principal) {
        return ApiResponse.success(service.snapshot(principal.organizationId()), RequestContext.requestId());
    }

    @GetMapping("/onboardings")
    ApiResponse<List<OnboardingReportRow>> onboardings(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        var result = service.onboardings(principal.organizationId(), page, size, status);
        return ApiResponse.success(result.items(), Map.of(
                "page", result.page(), "size", result.size(), "totalElements", result.totalElements(),
                "totalPages", result.totalPages()), RequestContext.requestId());
    }
}
