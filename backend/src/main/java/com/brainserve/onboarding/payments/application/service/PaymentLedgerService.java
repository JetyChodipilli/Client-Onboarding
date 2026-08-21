package com.brainserve.onboarding.payments.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.billing.domain.model.BillingActorType;
import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.domain.model.InvoiceStatus;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.payments.api.response.PaymentResponse;
import com.brainserve.onboarding.payments.domain.model.Payment;
import com.brainserve.onboarding.payments.domain.model.PaymentActorType;
import com.brainserve.onboarding.payments.domain.model.PaymentStatus;
import com.brainserve.onboarding.payments.domain.model.PaymentTransaction;
import com.brainserve.onboarding.payments.domain.model.PaymentTransactionType;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Immutable financial-ledger writer and reconciler. */
@Service
public class PaymentLedgerService {
    private static final EnumSet<PaymentTransactionType> CAPTURE_TYPES = EnumSet.of(PaymentTransactionType.CAPTURE, PaymentTransactionType.MANUAL_CAPTURE);
    private static final EnumSet<PaymentTransactionType> REFUND_TYPES = EnumSet.of(PaymentTransactionType.REFUND, PaymentTransactionType.MANUAL_REFUND);

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PaymentLedgerService(InvoiceRepository invoices, PaymentRepository payments,
                                PaymentTransactionRepository transactions, ActivityTimelineService activity,
                                AuditService audit, OutboxService outbox, ApplicationEventPublisher events, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.transactions = transactions;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse manualCapture(TenantPrincipal principal, UUID invoiceId, long amountMinor, String reason,
                                         String idempotencyKey, HttpServletRequest request) {
        if (amountMinor <= 0) throw invalid("Manual payment amount must be positive.");
        String key = normalizeIdempotencyKey(idempotencyKey);
        Invoice invoice = requireInvoiceForUpdate(principal.organizationId(), invoiceId);
        requireAcceptingPayment(invoice);
        if (amountMinor > invoice.getBalanceDueMinor()) throw invalid("Manual payment cannot exceed the outstanding invoice balance.");
        Payment existing = payments.findByOrganizationIdAndInvoiceIdAndSessionIdempotencyKey(principal.organizationId(), invoiceId, key).orElse(null);
        if (existing != null) {
            if (!"MANUAL".equals(existing.getProvider()) || existing.getRequestedAmountMinor() != amountMinor) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different payment command.");
            }
            return response(existing);
        }
        Instant now = clock.instant();
        Payment payment = new Payment(UUID.randomUUID(), principal.organizationId(), invoiceId, invoice.getProjectId(), "MANUAL",
                amountMinor, invoice.getCurrency(), key, principal.userId(), now);
        payment.apply(PaymentStatus.CAPTURED, amountMinor, 0, "manual_" + payment.getId(), null, now);
        payments.saveAndFlush(payment);
        transactions.saveAndFlush(new PaymentTransaction(UUID.randomUUID(), principal.organizationId(), invoiceId, payment.getId(), "MANUAL",
                null, "manual_" + UUID.randomUUID(), PaymentTransactionType.MANUAL_CAPTURE, amountMinor, invoice.getCurrency(),
                requireReason(reason), PaymentActorType.INTERNAL, principal.userId(), now, now));
        reconcileInvoice(invoice, BillingActorType.INTERNAL, principal.userId(), now);
        activity.record(principal.organizationId(), invoice.getClientId(), invoice.getProjectId(), principal.userId(), "PAYMENT_MANUAL_CAPTURED",
                "PAYMENT", payment.getId(), "Manual payment recorded", Map.of("invoiceId", invoiceId, "amountMinor", amountMinor, "currency", invoice.getCurrency()));
        audit.record(principal.organizationId(), principal.userId(), "PAYMENT_MANUAL_CAPTURED", "PAYMENT", payment.getId(), null,
                Map.of("invoiceId", invoiceId, "amountMinor", amountMinor, "currency", invoice.getCurrency(), "reason", reason), request);
        outbox.record(principal.organizationId(), "PAYMENT_CAPTURED", "PAYMENT", payment.getId(),
                Map.of("paymentId", payment.getId(), "invoiceId", invoiceId, "projectId", invoice.getProjectId(), "amountMinor", amountMinor, "manual", true));
        events.publishEvent(new PaymentRequirementChangedEvent(principal.organizationId(), invoiceId));
        return response(payment);
    }

    @Transactional
    public void reconcileByOperator(TenantPrincipal principal, UUID invoiceId, String reason, HttpServletRequest request) {
        Invoice invoice = requireInvoiceForUpdate(principal.organizationId(), invoiceId);
        long beforePaid = invoice.getAmountPaidMinor();
        long beforeRefunded = invoice.getAmountRefundedMinor();
        reconcileInvoice(invoice, BillingActorType.INTERNAL, principal.userId(), clock.instant());
        audit.record(principal.organizationId(), principal.userId(), "PAYMENT_RECONCILED", "INVOICE", invoiceId,
                Map.of("paidMinor", beforePaid, "refundedMinor", beforeRefunded),
                Map.of("paidMinor", invoice.getAmountPaidMinor(), "refundedMinor", invoice.getAmountRefundedMinor(), "reason", requireReason(reason)), request);
        events.publishEvent(new PaymentRequirementChangedEvent(principal.organizationId(), invoiceId));
    }

    void reconcileInvoice(Invoice invoice, BillingActorType actorType, UUID actorId, Instant now) {
        long captured = transactions.sumByTypes(invoice.getOrganizationId(), invoice.getId(), CAPTURE_TYPES);
        long refunded = transactions.sumByTypes(invoice.getOrganizationId(), invoice.getId(), REFUND_TYPES);
        try { invoice.reconcile(captured, refunded, actorType, actorId, now); }
        catch (IllegalStateException ex) { throw new ApiException(HttpStatus.CONFLICT, "INVOICE_STATE_INVALID", ex.getMessage()); }
        invoices.saveAndFlush(invoice);
    }

    void reconcilePayment(Payment payment, PaymentStatus target, String providerPaymentId, String failureReason, Instant now) {
        long captured = transactions.sumPaymentByTypes(payment.getOrganizationId(), payment.getId(), CAPTURE_TYPES);
        long refunded = transactions.sumPaymentByTypes(payment.getOrganizationId(), payment.getId(), REFUND_TYPES);
        payment.apply(target, captured, refunded, providerPaymentId, failureReason, now);
        payments.saveAndFlush(payment);
    }

    private Invoice requireInvoiceForUpdate(UUID org, UUID id) {
        return invoices.findForUpdate(org, id).orElseThrow(PaymentLedgerService::notFound);
    }

    static void requireAcceptingPayment(Invoice invoice) {
        if (!(invoice.getStatus() == InvoiceStatus.SENT || invoice.getStatus() == InvoiceStatus.VIEWED
                || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID || invoice.getStatus() == InvoiceStatus.OVERDUE
                || invoice.getStatus() == InvoiceStatus.PARTIALLY_REFUNDED)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVOICE_STATE_INVALID", "This invoice is not accepting payments.");
        }
        if (invoice.getBalanceDueMinor() <= 0) throw new ApiException(HttpStatus.CONFLICT, "INVOICE_ALREADY_SETTLED", "This invoice has no outstanding balance.");
    }

    static PaymentResponse response(Payment p) {
        return new PaymentResponse(p.getId(), p.getProvider(), p.getProviderPaymentId(), p.getRequestedAmountMinor(),
                p.getCapturedAmountMinor(), p.getRefundedAmountMinor(), p.getCurrency(), p.getStatus(), p.getCheckoutUrl(),
                p.getSessionExpiresAt(), p.getFailureReason(), p.getCreatedAt(), p.getVersion());
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) throw invalid("A valid Idempotency-Key is required.");
        return value;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw invalid("A reason is required for a controlled financial override.");
        String value = reason.trim();
        if (value.length() > 2000) throw invalid("Financial override reason is too long.");
        return value;
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested payment resource was not found."); }
}
