package com.brainserve.onboarding.payments.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.domain.model.InvoiceStatus;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.payments.api.response.PaymentSessionResponse;
import com.brainserve.onboarding.payments.domain.model.Payment;
import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentRepository;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGateway;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGatewayRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates provider checkout sessions without holding database locks across provider I/O. */
@Service
public class PaymentSessionService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ClientPortalAccessService clientAccess;
    private final PaymentGatewayRegistry gateways;
    private final PaymentProperties properties;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PaymentSessionService(InvoiceRepository invoices, PaymentRepository payments,
                                 ClientPortalAccessService clientAccess, PaymentGatewayRegistry gateways,
                                 PaymentProperties properties, ActivityTimelineService activity,
                                 AuditService audit, OutboxService outbox, Clock clock, TransactionTemplate tx) {
        this.invoices = invoices;
        this.payments = payments;
        this.clientAccess = clientAccess;
        this.gateways = gateways;
        this.properties = properties;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
        this.tx = tx;
    }

    public PaymentSessionResponse createInternal(TenantPrincipal principal, UUID invoiceId, Long requestedAmount,
                                                 String idempotencyKey, HttpServletRequest request) {
        return create(principal.organizationId(), principal.userId(), invoiceId, null, requestedAmount,
                normalizeKey(idempotencyKey), false, request);
    }

    public PaymentSessionResponse createClient(ClientPrincipal principal, UUID projectId, UUID invoiceId,
                                               Long requestedAmount, String idempotencyKey, HttpServletRequest request) {
        clientAccess.requireProjectActionAccess(principal.organizationId(), principal.userId(), projectId);
        return create(principal.organizationId(), principal.userId(), invoiceId, projectId, requestedAmount,
                normalizeKey(idempotencyKey), true, request);
    }

    private PaymentSessionResponse create(UUID organizationId, UUID actorId, UUID invoiceId, UUID expectedProjectId,
                                          Long requestedAmount, String key, boolean clientActor,
                                          HttpServletRequest request) {
        Prepared prepared = tx.execute(status -> prepare(organizationId, actorId, invoiceId, expectedProjectId, requestedAmount, key));
        if (prepared == null) throw new IllegalStateException("Payment-session transaction returned no result");
        if (prepared.existingResponse() != null) return prepared.existingResponse();

        PaymentGateway gateway = gateways.byCode(prepared.provider());
        PaymentGateway.CheckoutSession checkout;
        try {
            checkout = gateway.createCheckout(new PaymentGateway.CheckoutCommand(
                    organizationId, prepared.projectId(), invoiceId, prepared.paymentId(), key,
                    prepared.amountMinor(), prepared.currency(), clock.instant().plus(properties.sessionTtl())));
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(status -> payments.findForUpdate(organizationId, prepared.paymentId()).ifPresent(payment -> {
                if (payment.getStatus() == com.brainserve.onboarding.payments.domain.model.PaymentStatus.INITIATED) {
                    payment.failBeforeSession("Provider session creation failed", clock.instant());
                    payments.saveAndFlush(payment);
                }
            }));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                    "Payment processing is temporarily unavailable. Please try again later.");
        }

        return tx.execute(status -> {
            Payment payment = payments.findForUpdate(organizationId, prepared.paymentId()).orElseThrow(PaymentSessionService::notFound);
            if (payment.getStatus() == com.brainserve.onboarding.payments.domain.model.PaymentStatus.INITIATED) {
                payment.sessionCreated(checkout.providerPaymentId(), checkout.providerSessionId(), checkout.checkoutUrl(), checkout.expiresAt(), clock.instant());
                payments.saveAndFlush(payment);
                String actorType = clientActor ? "CLIENT" : "INTERNAL";
                if (clientActor) {
                    activity.recordClient(organizationId, prepared.clientId(), prepared.projectId(), actorId,
                            "PAYMENT_SESSION_CREATED", "PAYMENT", payment.getId(), "Payment session created",
                            Map.of("invoiceId", invoiceId, "amountMinor", payment.getRequestedAmountMinor(), "currency", payment.getCurrency()));
                    audit.recordClient(organizationId, actorId, "PAYMENT_SESSION_CREATED", "PAYMENT", payment.getId(), null,
                            Map.of("invoiceId", invoiceId, "amountMinor", payment.getRequestedAmountMinor(), "provider", payment.getProvider()), request);
                } else {
                    activity.record(organizationId, prepared.clientId(), prepared.projectId(), actorId,
                            "PAYMENT_SESSION_CREATED", "PAYMENT", payment.getId(), "Payment session created",
                            Map.of("invoiceId", invoiceId, "amountMinor", payment.getRequestedAmountMinor(), "currency", payment.getCurrency()));
                    audit.record(organizationId, actorId, "PAYMENT_SESSION_CREATED", "PAYMENT", payment.getId(), null,
                            Map.of("invoiceId", invoiceId, "amountMinor", payment.getRequestedAmountMinor(), "provider", payment.getProvider()), request);
                }
                outbox.record(organizationId, "PAYMENT_SESSION_CREATED", "PAYMENT", payment.getId(),
                        Map.of("paymentId", payment.getId(), "invoiceId", invoiceId, "projectId", prepared.projectId(),
                                "amountMinor", payment.getRequestedAmountMinor(), "actorType", actorType));
            }
            return response(payment);
        });
    }

    private Prepared prepare(UUID organizationId, UUID actorId, UUID invoiceId, UUID expectedProjectId,
                             Long requestedAmount, String key) {
        Invoice invoice = invoices.findForUpdate(organizationId, invoiceId).orElseThrow(PaymentSessionService::notFound);
        if (expectedProjectId != null && !expectedProjectId.equals(invoice.getProjectId())) throw notFound();
        if (!clientVisible(invoice) && expectedProjectId != null) throw notFound();
        PaymentLedgerService.requireAcceptingPayment(invoice);
        long amount = requestedAmount == null ? invoice.getBalanceDueMinor() : requestedAmount;
        if (amount <= 0 || amount > invoice.getBalanceDueMinor()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_INVALID", "Payment amount must be positive and cannot exceed the outstanding balance.");
        }
        PaymentGateway gateway = gateways.current();
        Payment existing = payments.findByOrganizationIdAndInvoiceIdAndSessionIdempotencyKey(organizationId, invoiceId, key).orElse(null);
        if (existing != null) {
            if (existing.getRequestedAmountMinor() != amount || !existing.getProvider().equals(gateway.providerCode())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different payment session.");
            }
            if (existing.getCheckoutUrl() == null || existing.getSessionExpiresAt() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SESSION_FAILED", "The previous payment-session attempt did not complete. Use a new Idempotency-Key.");
            }
            return new Prepared(existing.getId(), invoice.getProjectId(), invoice.getClientId(), amount, invoice.getCurrency(),
                    existing.getProvider(), response(existing));
        }
        Payment payment = new Payment(UUID.randomUUID(), organizationId, invoiceId, invoice.getProjectId(), gateway.providerCode(),
                amount, invoice.getCurrency(), key, actorId, clock.instant());
        payments.saveAndFlush(payment);
        return new Prepared(payment.getId(), invoice.getProjectId(), invoice.getClientId(), amount, invoice.getCurrency(), gateway.providerCode(), null);
    }

    private static PaymentSessionResponse response(Payment payment) {
        return new PaymentSessionResponse(payment.getId(), payment.getProvider(), payment.getCheckoutUrl(),
                payment.getSessionExpiresAt(), payment.getRequestedAmountMinor(), payment.getCurrency());
    }

    private static boolean clientVisible(Invoice invoice) {
        return invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.VOID && invoice.getStatus() != InvoiceStatus.CANCELLED;
    }

    private static String normalizeKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "A valid Idempotency-Key is required.");
        }
        return value;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested payment resource was not found.");
    }

    private record Prepared(UUID paymentId, UUID projectId, UUID clientId, long amountMinor, String currency,
                            String provider, PaymentSessionResponse existingResponse) {}
}
