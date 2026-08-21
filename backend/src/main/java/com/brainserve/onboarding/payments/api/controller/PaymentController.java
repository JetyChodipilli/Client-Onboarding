package com.brainserve.onboarding.payments.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.payments.api.request.ManualPaymentRequest;
import com.brainserve.onboarding.payments.api.request.PaymentSessionRequest;
import com.brainserve.onboarding.payments.api.request.RefundRequestDto;
import com.brainserve.onboarding.payments.api.response.PaymentResponse;
import com.brainserve.onboarding.payments.api.response.PaymentSessionResponse;
import com.brainserve.onboarding.payments.api.response.RefundResponse;
import com.brainserve.onboarding.payments.application.service.PaymentLedgerService;
import com.brainserve.onboarding.payments.application.service.PaymentSessionService;
import com.brainserve.onboarding.payments.application.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentSessionService sessions;
    private final PaymentLedgerService ledger;
    private final RefundService refunds;

    public PaymentController(PaymentSessionService sessions, PaymentLedgerService ledger, RefundService refunds) {
        this.sessions = sessions; this.ledger = ledger; this.refunds = refunds;
    }

    @PostMapping("/invoices/{invoiceId}/payment-session")
    @PreAuthorize("hasAnyAuthority('INVOICE_SEND','PAYMENT_OVERRIDE')")
    ApiResponse<PaymentSessionResponse> createSession(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID invoiceId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody(required = false) PaymentSessionRequest body,
            HttpServletRequest request) {
        Long amount = body == null ? null : body.amountMinor();
        return ApiResponse.success(sessions.createInternal(principal, invoiceId, amount, idempotencyKey, request), RequestContext.requestId());
    }

    @PostMapping("/invoices/{invoiceId}/manual-payment")
    @PreAuthorize("hasAuthority('PAYMENT_OVERRIDE')")
    ApiResponse<PaymentResponse> manualPayment(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID invoiceId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody ManualPaymentRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(ledger.manualCapture(principal, invoiceId, body.amountMinor(), body.reason(), idempotencyKey, request), RequestContext.requestId());
    }

    @PostMapping("/invoices/{invoiceId}/reconcile")
    @PreAuthorize("hasAuthority('PAYMENT_OVERRIDE')")
    ApiResponse<Void> reconcile(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID invoiceId,
            @RequestParam @Size(min = 1, max = 2000) String reason,
            HttpServletRequest request) {
        ledger.reconcileByOperator(principal, invoiceId, reason, request);
        return ApiResponse.success(null, RequestContext.requestId());
    }

    @PostMapping("/invoices/{invoiceId}/payments/{paymentId}/refunds")
    @PreAuthorize("hasAuthority('PAYMENT_OVERRIDE')")
    ApiResponse<RefundResponse> refund(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID invoiceId,
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody RefundRequestDto body,
            HttpServletRequest request) {
        return ApiResponse.success(refunds.request(principal, invoiceId, paymentId, body.amountMinor(), body.reason(), idempotencyKey, request), RequestContext.requestId());
    }
}
