package com.brainserve.onboarding.payments.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.billing.domain.model.BillingActorType;
import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.payments.api.response.RefundResponse;
import com.brainserve.onboarding.payments.domain.model.Payment;
import com.brainserve.onboarding.payments.domain.model.PaymentActorType;
import com.brainserve.onboarding.payments.domain.model.PaymentStatus;
import com.brainserve.onboarding.payments.domain.model.PaymentTransaction;
import com.brainserve.onboarding.payments.domain.model.PaymentTransactionType;
import com.brainserve.onboarding.payments.domain.model.Refund;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentTransactionRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.RefundRepository;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGateway;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGatewayRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Controlled refund orchestration. Provider refunds become financially authoritative only on verified callbacks. */
@Service
public class RefundService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final RefundRepository refunds;
    private final PaymentGatewayRegistry gateways;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    public RefundService(InvoiceRepository invoices, PaymentRepository payments, PaymentTransactionRepository transactions,
                         RefundRepository refunds, PaymentGatewayRegistry gateways, ActivityTimelineService activity,
                         AuditService audit, OutboxService outbox, ApplicationEventPublisher events, Clock clock,
                         TransactionTemplate tx) {
        this.invoices = invoices; this.payments = payments; this.transactions = transactions; this.refunds = refunds;
        this.gateways = gateways; this.activity = activity; this.audit = audit; this.outbox = outbox; this.events = events;
        this.clock = clock; this.tx = tx;
    }

    public RefundResponse request(TenantPrincipal principal, UUID invoiceId, UUID paymentId, long amountMinor,
                                  String reason, String idempotencyKey, HttpServletRequest request) {
        String key = normalizeKey(idempotencyKey);
        String normalizedReason = requireReason(reason);
        Prepared prepared = tx.execute(status -> prepare(principal, invoiceId, paymentId, amountMinor, normalizedReason, key));
        if (prepared == null) throw new IllegalStateException("Refund transaction returned no result");
        if (prepared.existing() != null) return response(prepared.existing());

        if ("MANUAL".equals(prepared.provider())) {
            return tx.execute(status -> completeManual(principal, prepared.refundId(), invoiceId, paymentId, amountMinor, normalizedReason, request));
        }

        PaymentGateway gateway = gateways.byCode(prepared.provider());
        PaymentGateway.RefundRequest providerResult;
        try {
            providerResult = gateway.requestRefund(new PaymentGateway.RefundCommand(principal.organizationId(), invoiceId,
                    paymentId, prepared.refundId(), prepared.providerPaymentId(), key, amountMinor, prepared.currency(), normalizedReason));
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(status -> refunds.findForUpdate(principal.organizationId(), prepared.refundId()).ifPresent(refund -> {
                refund.failed("Provider refund request failed", clock.instant());
                refunds.saveAndFlush(refund);
            }));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                    "The refund provider is temporarily unavailable. The invoice was not changed.");
        }

        return tx.execute(status -> {
            Refund refund = refunds.findForUpdate(principal.organizationId(), prepared.refundId()).orElseThrow(RefundService::notFound);
            if (refund.getProviderRefundId() == null) {
                refund.providerAccepted(providerResult.providerRefundId(), clock.instant());
                refunds.saveAndFlush(refund);
                activity.record(principal.organizationId(), prepared.clientId(), prepared.projectId(), principal.userId(),
                        "REFUND_REQUESTED", "REFUND", refund.getId(), "Refund requested",
                        Map.of("invoiceId", invoiceId, "paymentId", paymentId, "amountMinor", amountMinor, "currency", prepared.currency()));
                audit.record(principal.organizationId(), principal.userId(), "REFUND_REQUESTED", "REFUND", refund.getId(), null,
                        Map.of("invoiceId", invoiceId, "paymentId", paymentId, "amountMinor", amountMinor, "reason", normalizedReason), request);
                outbox.record(principal.organizationId(), "REFUND_REQUESTED", "REFUND", refund.getId(),
                        Map.of("refundId", refund.getId(), "invoiceId", invoiceId, "paymentId", paymentId, "projectId", prepared.projectId(), "amountMinor", amountMinor));
            }
            return response(refund);
        });
    }

    private Prepared prepare(TenantPrincipal principal, UUID invoiceId, UUID paymentId, long amountMinor, String reason, String key) {
        if (amountMinor <= 0) throw invalid("Refund amount must be positive.");
        Invoice invoice = invoices.findForUpdate(principal.organizationId(), invoiceId).orElseThrow(RefundService::notFound);
        Payment payment = payments.findForUpdate(principal.organizationId(), paymentId).orElseThrow(RefundService::notFound);
        if (!payment.getInvoiceId().equals(invoiceId)) throw notFound();
        long available = Math.max(0L, payment.getCapturedAmountMinor() - payment.getRefundedAmountMinor());
        if (amountMinor > available) throw invalid("Refund amount cannot exceed the captured amount that remains refundable.");
        Refund existing = refunds.findByOrganizationIdAndPaymentIdAndIdempotencyKey(principal.organizationId(), paymentId, key).orElse(null);
        if (existing != null) {
            if (existing.getAmountMinor() != amountMinor || !existing.getReason().equals(reason)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different refund request.");
            }
            return new Prepared(existing.getId(), payment.getProvider(), payment.getProviderPaymentId(), invoice.getProjectId(), invoice.getClientId(), invoice.getCurrency(), existing);
        }
        if (!"MANUAL".equals(payment.getProvider()) && (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_PROVIDER_REFERENCE_MISSING", "The payment does not have a provider reference that can be refunded.");
        }
        Refund refund = new Refund(UUID.randomUUID(), principal.organizationId(), invoiceId, paymentId, payment.getProvider(),
                amountMinor, invoice.getCurrency(), reason, key, principal.userId(), clock.instant());
        refunds.saveAndFlush(refund);
        return new Prepared(refund.getId(), payment.getProvider(), payment.getProviderPaymentId(), invoice.getProjectId(), invoice.getClientId(), invoice.getCurrency(), null);
    }

    private RefundResponse completeManual(TenantPrincipal principal, UUID refundId, UUID invoiceId, UUID paymentId,
                                          long amountMinor, String reason, HttpServletRequest request) {
        Refund refund = refunds.findForUpdate(principal.organizationId(), refundId).orElseThrow(RefundService::notFound);
        Payment payment = payments.findForUpdate(principal.organizationId(), paymentId).orElseThrow(RefundService::notFound);
        Invoice invoice = invoices.findForUpdate(principal.organizationId(), invoiceId).orElseThrow(RefundService::notFound);
        Instant now = clock.instant();
        transactions.saveAndFlush(new PaymentTransaction(UUID.randomUUID(), principal.organizationId(), invoiceId, paymentId,
                "MANUAL", null, "manual_refund_" + refundId, PaymentTransactionType.MANUAL_REFUND, amountMinor,
                invoice.getCurrency(), reason, PaymentActorType.INTERNAL, principal.userId(), now, now));
        long captured = transactions.sumPaymentByTypes(principal.organizationId(), paymentId,
                java.util.EnumSet.of(PaymentTransactionType.CAPTURE, PaymentTransactionType.MANUAL_CAPTURE));
        long refunded = transactions.sumPaymentByTypes(principal.organizationId(), paymentId,
                java.util.EnumSet.of(PaymentTransactionType.REFUND, PaymentTransactionType.MANUAL_REFUND));
        PaymentStatus paymentStatus = refunded >= captured ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        payment.apply(paymentStatus, captured, refunded, payment.getProviderPaymentId(), null, now);
        payments.saveAndFlush(payment);
        long invoiceCaptured = transactions.sumByTypes(principal.organizationId(), invoiceId,
                java.util.EnumSet.of(PaymentTransactionType.CAPTURE, PaymentTransactionType.MANUAL_CAPTURE));
        long invoiceRefunded = transactions.sumByTypes(principal.organizationId(), invoiceId,
                java.util.EnumSet.of(PaymentTransactionType.REFUND, PaymentTransactionType.MANUAL_REFUND));
        invoice.reconcile(invoiceCaptured, invoiceRefunded, BillingActorType.INTERNAL, principal.userId(), now);
        invoices.saveAndFlush(invoice);
        refund.providerAccepted("manual_" + refundId, now);
        refund.captured(now);
        refunds.saveAndFlush(refund);
        activity.record(principal.organizationId(), invoice.getClientId(), invoice.getProjectId(), principal.userId(),
                "REFUND_CAPTURED", "REFUND", refundId, "Manual refund recorded",
                Map.of("invoiceId", invoiceId, "paymentId", paymentId, "amountMinor", amountMinor, "currency", invoice.getCurrency()));
        audit.record(principal.organizationId(), principal.userId(), "REFUND_CAPTURED", "REFUND", refundId, null,
                Map.of("invoiceId", invoiceId, "paymentId", paymentId, "amountMinor", amountMinor, "reason", reason, "manual", true), request);
        outbox.record(principal.organizationId(), "REFUND_CAPTURED", "REFUND", refundId,
                Map.of("refundId", refundId, "invoiceId", invoiceId, "paymentId", paymentId, "projectId", invoice.getProjectId(), "amountMinor", amountMinor, "manual", true));
        events.publishEvent(new PaymentRequirementChangedEvent(principal.organizationId(), invoiceId));
        return response(refund);
    }

    public static RefundResponse response(Refund r) {
        return new RefundResponse(r.getId(), r.getPaymentId(), r.getProvider(), r.getProviderRefundId(), r.getAmountMinor(),
                r.getCurrency(), r.getStatus(), r.getReason(), r.getFailureReason(), r.getRequestedAt(), r.getCompletedAt(), r.getVersion());
    }

    private static String normalizeKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) throw invalid("A valid Idempotency-Key is required.");
        return value;
    }
    private static String requireReason(String value) {
        if (value == null || value.isBlank()) throw invalid("Refund reason is required.");
        String normalized = value.trim();
        if (normalized.length() > 2000) throw invalid("Refund reason is too long.");
        return normalized;
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "REFUND_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested refund resource was not found."); }
    private record Prepared(UUID refundId, String provider, String providerPaymentId, UUID projectId, UUID clientId,
                            String currency, Refund existing) {}
}
