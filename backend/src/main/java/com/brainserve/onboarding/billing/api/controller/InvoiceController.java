package com.brainserve.onboarding.billing.api.controller;

import com.brainserve.onboarding.billing.api.request.CreateInvoiceRequest;
import com.brainserve.onboarding.billing.api.request.InvoiceVersionRequest;
import com.brainserve.onboarding.billing.api.response.InvoiceResponse;
import com.brainserve.onboarding.billing.api.response.InvoiceSummaryResponse;
import com.brainserve.onboarding.billing.application.service.InvoiceService;
import com.brainserve.onboarding.billing.domain.model.InvoiceStatus;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    ApiResponse<InvoiceResponse> create(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody CreateInvoiceRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.create(principal, idempotencyKey, body, request), RequestContext.requestId());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVOICE_CREATE','INVOICE_SEND','PAYMENT_OVERRIDE')")
    ApiResponse<List<InvoiceSummaryResponse>> list(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = service.list(principal, projectId, status, page, size);
        return ApiResponse.success(result.items(), Map.of("page", result.page(), "size", result.size(),
                "totalElements", result.totalElements(), "totalPages", result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyAuthority('INVOICE_CREATE','INVOICE_SEND','PAYMENT_OVERRIDE')")
    ApiResponse<InvoiceResponse> detail(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID invoiceId) {
        return ApiResponse.success(service.detail(principal, invoiceId), RequestContext.requestId());
    }

    @PostMapping("/{invoiceId}/send")
    @PreAuthorize("hasAuthority('INVOICE_SEND')")
    ApiResponse<InvoiceResponse> send(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID invoiceId,
                                      @Valid @RequestBody InvoiceVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.send(principal, invoiceId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{invoiceId}/cancel")
    @PreAuthorize("hasAuthority('INVOICE_SEND')")
    ApiResponse<InvoiceResponse> cancel(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID invoiceId,
                                        @Valid @RequestBody InvoiceVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.cancel(principal, invoiceId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{invoiceId}/void")
    @PreAuthorize("hasAuthority('INVOICE_SEND')")
    ApiResponse<InvoiceResponse> voidInvoice(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID invoiceId,
                                             @Valid @RequestBody InvoiceVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.voidInvoice(principal, invoiceId, body.version(), request), RequestContext.requestId());
    }
}
