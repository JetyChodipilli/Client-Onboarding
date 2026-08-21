package com.brainserve.onboarding.payments.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.billing.domain.model.BillingActorType;
import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.payments.domain.model.Payment;
import com.brainserve.onboarding.payments.domain.model.PaymentActorType;
import com.brainserve.onboarding.payments.domain.model.PaymentStatus;
import com.brainserve.onboarding.payments.domain.model.PaymentTransaction;
import com.brainserve.onboarding.payments.domain.model.PaymentTransactionType;
import com.brainserve.onboarding.payments.domain.model.Refund;
import com.brainserve.onboarding.payments.domain.model.WebhookEvent;
import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentTransactionRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.RefundRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.WebhookEventRepository;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGateway;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGatewayRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies provider callbacks before any financial mutation and applies each provider event id at most once. */
@Service
public class PaymentWebhookService {
    private static final EnumSet<PaymentTransactionType> CAPTURES = EnumSet.of(PaymentTransactionType.CAPTURE, PaymentTransactionType.MANUAL_CAPTURE);
    private static final EnumSet<PaymentTransactionType> REFUNDS = EnumSet.of(PaymentTransactionType.REFUND, PaymentTransactionType.MANUAL_REFUND);

    private final PaymentGatewayRegistry gateways;
    private final PaymentProperties properties;
    private final WebhookEventRepository webhookEvents;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final RefundRepository refunds;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ApplicationEventPublisher applicationEvents;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PaymentWebhookService(PaymentGatewayRegistry gateways, PaymentProperties properties,
                                 WebhookEventRepository webhookEvents, InvoiceRepository invoices,
                                 PaymentRepository payments, PaymentTransactionRepository transactions,
                                 RefundRepository refunds, AuditService audit, OutboxService outbox,
                                 ApplicationEventPublisher applicationEvents, Clock clock, TransactionTemplate tx) {
        this.gateways = gateways; this.properties = properties; this.webhookEvents = webhookEvents; this.invoices = invoices;
        this.payments = payments; this.transactions = transactions; this.refunds = refunds; this.audit = audit;
        this.outbox = outbox; this.applicationEvents = applicationEvents; this.clock = clock; this.tx = tx;
    }

    public WebhookResult process(String providerCode, byte[] body, Map<String, String> headers) {
        if (body == null || body.length == 0 || body.length > properties.maxWebhookBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_PAYLOAD_INVALID", "Payment webhook payload size is invalid.");
        }
        PaymentGateway gateway = gateways.byCode(providerCode);
        PaymentGateway.VerifiedWebhook verified;
        try {
            verified = gateway.verifyWebhook(body, headers, clock.instant());
        } catch (SecurityException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID", "Payment webhook signature is invalid.");
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_PAYLOAD_INVALID", "Payment webhook payload is invalid.");
        }
        String hash = CryptoSupport.sha256Hex(body);
        try {
            WebhookResult result = tx.execute(status -> apply(gateway.providerCode(), verified, hash));
            return result == null ? new WebhookResult(false, "IGNORED") : result;
        } catch (DataIntegrityViolationException ex) {
            WebhookEvent existing = webhookEvents.findByProviderAndProviderEventId(gateway.providerCode(), verified.eventId()).orElse(null);
            if (existing != null && existing.getPayloadHash().equals(hash)) return new WebhookResult(true, existing.getProcessingStatus().name());
            throw new ApiException(HttpStatus.CONFLICT, "WEBHOOK_EVENT_COLLISION", "Provider event id was reused with different content.");
        }
    }

    private WebhookResult apply(String provider, PaymentGateway.VerifiedWebhook event, String payloadHash) {
        WebhookEvent existing = webhookEvents.findByProviderAndProviderEventId(provider, event.eventId()).orElse(null);
        if (existing != null) {
            if (!existing.getPayloadHash().equals(payloadHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "WEBHOOK_EVENT_COLLISION", "Provider event id was reused with different content.");
            }
            return new WebhookResult(true, existing.getProcessingStatus().name());
        }
        WebhookEvent record = new WebhookEvent(UUID.randomUUID(), event.organizationId(), provider, event.eventId(),
                event.eventType(), payloadHash, clock.instant());
        webhookEvents.saveAndFlush(record);

        Invoice invoice = invoices.findForUpdate(event.organizationId(), event.invoiceId()).orElseThrow(PaymentWebhookService::notFound);
        Payment payment = payments.findForUpdate(event.organizationId(), event.paymentId()).orElseThrow(PaymentWebhookService::notFound);
        if (!payment.getInvoiceId().equals(invoice.getId()) || !payment.getProvider().equalsIgnoreCase(provider)) throw notFound();
        if (!payment.getCurrency().equalsIgnoreCase(event.currency())) {
            record.failed("Webhook currency does not match payment", clock.instant()); webhookEvents.saveAndFlush(record);
            throw invalid("Webhook currency does not match payment.");
        }
        if (event.providerPaymentId() != null && payment.getProviderPaymentId() != null
                && !payment.getProviderPaymentId().equals(event.providerPaymentId())) {
            record.failed("Provider payment reference mismatch", clock.instant()); webhookEvents.saveAndFlush(record);
            throw invalid("Provider payment reference does not match the stored payment.");
        }

        String type = event.eventType().trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        Instant now = clock.instant();
        switch (type) {
            case "PAYMENT_PENDING" -> append(payment, event, provider, PaymentTransactionType.PENDING, 0, PaymentStatus.PENDING, null, now);
            case "PAYMENT_AUTHORIZED" -> append(payment, event, provider, PaymentTransactionType.AUTHORIZED, 0, PaymentStatus.AUTHORIZED, null, now);
            case "PAYMENT_CAPTURED" -> capture(invoice, payment, event, provider, now);
            case "PAYMENT_FAILED" -> append(payment, event, provider, PaymentTransactionType.FAILURE, 0, PaymentStatus.FAILED, event.reason(), now);
            case "PAYMENT_CANCELLED" -> append(payment, event, provider, PaymentTransactionType.CANCELLATION, 0, PaymentStatus.CANCELLED, event.reason(), now);
            case "REFUND_CAPTURED" -> refundCaptured(invoice, payment, event, provider, now);
            case "REFUND_FAILED" -> refundFailed(payment, event, provider, now);
            default -> {
                record.ignored(now); webhookEvents.saveAndFlush(record);
                return new WebhookResult(false, "IGNORED");
            }
        }
        reconcileInvoice(invoice, now);
        record.processed(now);
        webhookEvents.saveAndFlush(record);
        audit.recordApplication(event.organizationId(), null, "PROVIDER", "PAYMENT_WEBHOOK_PROCESSED", "INVOICE", invoice.getId(), null,
                Map.of("provider", provider, "providerEventId", event.eventId(), "eventType", event.eventType(), "paymentId", payment.getId()));
        outbox.record(event.organizationId(), type.equals("PAYMENT_CAPTURED") ? "PAYMENT_CONFIRMED" : "PAYMENT_EVENT_PROCESSED",
                "PAYMENT", payment.getId(), Map.of("paymentId", payment.getId(), "invoiceId", invoice.getId(), "projectId", invoice.getProjectId(),
                        "eventType", event.eventType(), "providerEventId", event.eventId()));
        applicationEvents.publishEvent(new PaymentRequirementChangedEvent(event.organizationId(), invoice.getId()));
        return new WebhookResult(false, "PROCESSED");
    }

    private void capture(Invoice invoice, Payment payment, PaymentGateway.VerifiedWebhook event, String provider, Instant now) {
        if (event.amountMinor() <= 0) throw invalid("Captured payment amount must be positive.");
        long already = transactions.sumPaymentByTypes(event.organizationId(), payment.getId(), CAPTURES);
        if (Math.addExact(already, event.amountMinor()) > payment.getRequestedAmountMinor()) throw invalid("Provider capture exceeds the payment-session amount.");
        append(payment, event, provider, PaymentTransactionType.CAPTURE, event.amountMinor(), PaymentStatus.CAPTURED, null, now);
    }

    private void refundCaptured(Invoice invoice, Payment payment, PaymentGateway.VerifiedWebhook event, String provider, Instant now) {
        if (event.amountMinor() <= 0 || event.providerRefundId() == null) throw invalid("Captured refund payload is incomplete.");
        Refund refund = refunds.findByProviderAndProviderRefundId(provider, event.providerRefundId()).orElseThrow(PaymentWebhookService::notFound);
        if (!refund.getOrganizationId().equals(event.organizationId()) || !refund.getInvoiceId().equals(invoice.getId()) || !refund.getPaymentId().equals(payment.getId())) throw notFound();
        if (refund.getAmountMinor() != event.amountMinor()) throw invalid("Provider refund amount does not match the requested refund.");
        long captured = transactions.sumPaymentByTypes(event.organizationId(), payment.getId(), CAPTURES);
        long refunded = transactions.sumPaymentByTypes(event.organizationId(), payment.getId(), REFUNDS);
        if (Math.addExact(refunded, event.amountMinor()) > captured) throw invalid("Provider refund exceeds captured payment value.");
        transactions.saveAndFlush(new PaymentTransaction(UUID.randomUUID(), event.organizationId(), invoice.getId(), payment.getId(), provider,
                event.eventId(), event.providerTransactionId(), PaymentTransactionType.REFUND, event.amountMinor(), invoice.getCurrency(),
                event.reason(), PaymentActorType.PROVIDER, null, event.occurredAt(), now));
        refund.captured(now); refunds.saveAndFlush(refund);
        long newRefunded = Math.addExact(refunded, event.amountMinor());
        payment.apply(newRefunded >= captured ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED,
                captured, newRefunded, event.providerPaymentId(), null, now);
        payments.saveAndFlush(payment);
    }

    private void refundFailed(Payment payment, PaymentGateway.VerifiedWebhook event, String provider, Instant now) {
        if (event.providerRefundId() == null) throw invalid("Failed refund payload is incomplete.");
        Refund refund = refunds.findByProviderAndProviderRefundId(provider, event.providerRefundId()).orElseThrow(PaymentWebhookService::notFound);
        if (!refund.getOrganizationId().equals(event.organizationId()) || !refund.getPaymentId().equals(payment.getId())) throw notFound();
        refund.failed(event.reason(), now); refunds.saveAndFlush(refund);
    }

    private void append(Payment payment, PaymentGateway.VerifiedWebhook event, String provider,
                        PaymentTransactionType transactionType, long amountMinor, PaymentStatus target,
                        String failureReason, Instant now) {
        transactions.saveAndFlush(new PaymentTransaction(UUID.randomUUID(), event.organizationId(), event.invoiceId(), payment.getId(), provider,
                event.eventId(), event.providerTransactionId(), transactionType, amountMinor, payment.getCurrency(), event.reason(),
                PaymentActorType.PROVIDER, null, event.occurredAt(), now));
        long captured = transactions.sumPaymentByTypes(event.organizationId(), payment.getId(), CAPTURES);
        long refunded = transactions.sumPaymentByTypes(event.organizationId(), payment.getId(), REFUNDS);
        payment.apply(target, captured, refunded, event.providerPaymentId(), failureReason, now);
        payments.saveAndFlush(payment);
    }

    private void reconcileInvoice(Invoice invoice, Instant now) {
        long captured = transactions.sumByTypes(invoice.getOrganizationId(), invoice.getId(), CAPTURES);
        long refunded = transactions.sumByTypes(invoice.getOrganizationId(), invoice.getId(), REFUNDS);
        invoice.reconcile(captured, refunded, BillingActorType.PROVIDER, null, now);
        invoices.saveAndFlush(invoice);
    }

    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_EVENT_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Referenced payment resource was not found."); }
    public record WebhookResult(boolean duplicate, String status) {}
}
