package com.brainserve.onboarding.billing.api.controller;

import com.brainserve.onboarding.billing.api.response.ClientInvoiceResponse;
import com.brainserve.onboarding.billing.api.response.InvoiceSummaryResponse;
import com.brainserve.onboarding.billing.application.service.InvoiceService;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.payments.api.request.PaymentSessionRequest;
import com.brainserve.onboarding.payments.api.response.PaymentSessionResponse;
import com.brainserve.onboarding.payments.application.service.PaymentSessionService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/client-portal/projects/{projectId}/invoices")
@PreAuthorize("hasAuthority('CLIENT_PORTAL')")
public class ClientBillingController {
    private final InvoiceService invoices;
    private final PaymentSessionService sessions;

    public ClientBillingController(InvoiceService invoices, PaymentSessionService sessions) {
        this.invoices = invoices; this.sessions = sessions;
    }

    @GetMapping
    ApiResponse<List<InvoiceSummaryResponse>> list(@AuthenticationPrincipal ClientPrincipal principal,
                                                    @PathVariable UUID projectId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        var result = invoices.clientList(principal, projectId, page, size);
        return ApiResponse.success(result.items(), Map.of("page", result.page(), "size", result.size(),
                "totalElements", result.totalElements(), "totalPages", result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{invoiceId}")
    ApiResponse<ClientInvoiceResponse> detail(@AuthenticationPrincipal ClientPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID invoiceId) {
        return ApiResponse.success(invoices.clientDetail(principal, projectId, invoiceId), RequestContext.requestId());
    }

    @PostMapping("/{invoiceId}/view")
    ApiResponse<ClientInvoiceResponse> markViewed(@AuthenticationPrincipal ClientPrincipal principal,
                                                   @PathVariable UUID projectId, @PathVariable UUID invoiceId,
                                                   HttpServletRequest request) {
        return ApiResponse.success(invoices.clientMarkViewed(principal, projectId, invoiceId, request), RequestContext.requestId());
    }

    @PostMapping("/{invoiceId}/payment-session")
    ApiResponse<PaymentSessionResponse> paymentSession(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable UUID projectId, @PathVariable UUID invoiceId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody(required = false) PaymentSessionRequest body,
            HttpServletRequest request) {
        Long amount = body == null ? null : body.amountMinor();
        return ApiResponse.success(sessions.createClient(principal, projectId, invoiceId, amount, idempotencyKey, request), RequestContext.requestId());
    }
}
