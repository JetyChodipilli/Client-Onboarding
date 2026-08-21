package com.brainserve.onboarding.billing.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.billing.api.request.CreateInvoiceRequest;
import com.brainserve.onboarding.billing.api.request.InvoiceItemRequest;
import com.brainserve.onboarding.billing.api.response.ClientInvoiceResponse;
import com.brainserve.onboarding.billing.api.response.ClientPaymentResponse;
import com.brainserve.onboarding.billing.api.response.InvoiceItemResponse;
import com.brainserve.onboarding.billing.api.response.InvoiceResponse;
import com.brainserve.onboarding.billing.api.response.InvoiceSummaryResponse;
import com.brainserve.onboarding.billing.domain.model.BillingActorType;
import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.domain.model.InvoiceItem;
import com.brainserve.onboarding.billing.domain.model.InvoiceStatus;
import com.brainserve.onboarding.billing.domain.model.PaymentPolicy;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceItemRepository;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import com.brainserve.onboarding.payments.api.response.PaymentResponse;
import com.brainserve.onboarding.payments.application.service.PaymentRequirementChangedEvent;
import com.brainserve.onboarding.payments.api.response.PaymentTransactionResponse;
import com.brainserve.onboarding.payments.api.response.RefundResponse;
import com.brainserve.onboarding.payments.domain.model.Payment;
import com.brainserve.onboarding.payments.domain.model.PaymentTransaction;
import com.brainserve.onboarding.payments.domain.model.Refund;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.PaymentTransactionRepository;
import com.brainserve.onboarding.payments.infrastructure.persistence.RefundRepository;
import com.brainserve.onboarding.project.application.service.ProjectWorkflowAccessService;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;
    private static final DateTimeFormatter INVOICE_MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final RefundRepository refunds;
    private final ProjectWorkflowAccessService projects;
    private final OnboardingStepAccessService steps;
    private final ClientPortalAccessService clientAccess;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, InvoiceItemRepository items, PaymentRepository payments,
                          PaymentTransactionRepository transactions, RefundRepository refunds,
                          ProjectWorkflowAccessService projects, OnboardingStepAccessService steps,
                          ClientPortalAccessService clientAccess, ActivityTimelineService activity,
                          AuditService audit, OutboxService outbox, ApplicationEventPublisher events, Clock clock) {
        this.invoices = invoices;
        this.items = items;
        this.payments = payments;
        this.transactions = transactions;
        this.refunds = refunds;
        this.projects = projects;
        this.steps = steps;
        this.clientAccess = clientAccess;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public InvoiceResponse create(TenantPrincipal principal, String idempotencyKey, CreateInvoiceRequest request,
                                  HttpServletRequest servletRequest) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        var project = projects.require(principal.organizationId(), request.projectId());
        if (project.status() == ProjectStatus.ARCHIVED || project.status() == ProjectStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_STATE_INVALID", "Closed projects cannot receive new invoices.");
        }

        InvoiceConfig config = resolveConfig(principal.organizationId(), request, project.id());
        List<InvoiceItem> prepared = prepareItems(principal.organizationId(), null, request.items(), principal.userId(), clock.instant());
        long subtotal = prepared.stream().mapToLong(InvoiceItem::getLineTotalMinor).reduce(0L, Math::addExact);
        long total = Math.addExact(subtotal, request.taxMinor());
        if (total > MAX_AMOUNT_MINOR) throw invalid("Invoice total exceeds the supported amount range.");
        long required = config.requiredAmountMinor() == null ? total : config.requiredAmountMinor();
        if (required > total) throw invalid("Required payment amount cannot exceed the invoice total.");
        if (config.paymentPolicy() == PaymentPolicy.NO_PAYMENT_REQUIRED && (total != 0 || !prepared.isEmpty())) {
            throw invalid("NO_PAYMENT_REQUIRED invoices must have no payable line items and a zero total.");
        }
        if (config.paymentPolicy() != PaymentPolicy.NO_PAYMENT_REQUIRED && total <= 0) {
            throw invalid("Payable invoices require a positive total.");
        }

        Invoice existing = invoices.findByOrganizationIdAndCreationIdempotencyKey(principal.organizationId(), key).orElse(null);
        if (existing != null) {
            if (!sameCreation(existing, project.id(), config, request, subtotal, total, required, prepared)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different invoice request.");
            }
            return detail(principal, existing.getId());
        }

        Instant now = clock.instant();
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, principal.organizationId(), project.id(), project.clientId(), config.onboardingId(),
                config.stepId(), invoiceNumber(now), config.paymentPolicy(), config.currency(), subtotal, request.taxMinor(), required,
                request.memo(), request.dueAt(), key, principal.userId(), now);
        invoices.saveAndFlush(invoice);
        if (!prepared.isEmpty()) {
            List<InvoiceItem> persisted = prepareItems(principal.organizationId(), invoiceId, request.items(), principal.userId(), now);
            items.saveAllAndFlush(persisted);
        }
        activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(), "INVOICE_CREATED", "INVOICE",
                invoiceId, "Invoice " + invoice.getInvoiceNumber() + " created", Map.of("invoiceNumber", invoice.getInvoiceNumber()));
        audit.record(principal.organizationId(), principal.userId(), "INVOICE_CREATED", "INVOICE", invoiceId, null,
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "totalMinor", total, "currency", invoice.getCurrency()), servletRequest);
        outbox.record(principal.organizationId(), "INVOICE_CREATED", "INVOICE", invoiceId,
                Map.of("invoiceId", invoiceId, "projectId", project.id(), "clientId", project.clientId(), "totalMinor", total, "currency", invoice.getCurrency()));
        if (invoice.paymentRequirementSatisfied()) {
            events.publishEvent(new PaymentRequirementChangedEvent(principal.organizationId(), invoiceId));
        }
        return detail(principal, invoiceId);
    }

    @Transactional(readOnly = true)
    public PageResult<InvoiceSummaryResponse> list(TenantPrincipal principal, UUID projectId, InvoiceStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Invoice> result;
        if (projectId != null) {
            projects.require(principal.organizationId(), projectId);
            result = status == null ? invoices.findAllByOrganizationIdAndProjectId(principal.organizationId(), projectId, pageable)
                    : invoices.findAllByOrganizationIdAndProjectIdAndStatus(principal.organizationId(), projectId, status, pageable);
        } else {
            result = status == null ? invoices.findAllByOrganizationId(principal.organizationId(), pageable)
                    : invoices.findAllByOrganizationIdAndStatus(principal.organizationId(), status, pageable);
        }
        return new PageResult<>(result.getContent().stream().map(InvoiceService::summary).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InvoiceResponse detail(TenantPrincipal principal, UUID invoiceId) {
        return internalResponse(require(principal.organizationId(), invoiceId));
    }

    @Transactional
    public InvoiceResponse send(TenantPrincipal principal, UUID invoiceId, long expectedVersion, HttpServletRequest request) {
        Invoice invoice = requireForUpdate(principal.organizationId(), invoiceId);
        requireVersion(invoice, expectedVersion);
        try { invoice.send(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        invoices.saveAndFlush(invoice);
        recordInternal(principal, invoice, "INVOICE_SENT", request);
        outbox.record(principal.organizationId(), "INVOICE_SENT", "INVOICE", invoiceId,
                Map.of("invoiceId", invoiceId, "projectId", invoice.getProjectId(), "clientId", invoice.getClientId()));
        return internalResponse(invoice);
    }

    @Transactional
    public InvoiceResponse cancel(TenantPrincipal principal, UUID invoiceId, long expectedVersion, HttpServletRequest request) {
        Invoice invoice = requireForUpdate(principal.organizationId(), invoiceId);
        requireVersion(invoice, expectedVersion);
        try { invoice.cancel(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        invoices.saveAndFlush(invoice);
        recordInternal(principal, invoice, "INVOICE_CANCELLED", request);
        outbox.record(principal.organizationId(), "INVOICE_CANCELLED", "INVOICE", invoiceId, Map.of("invoiceId", invoiceId, "projectId", invoice.getProjectId()));
        return internalResponse(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(TenantPrincipal principal, UUID invoiceId, long expectedVersion, HttpServletRequest request) {
        Invoice invoice = requireForUpdate(principal.organizationId(), invoiceId);
        requireVersion(invoice, expectedVersion);
        try { invoice.voidInvoice(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        invoices.saveAndFlush(invoice);
        recordInternal(principal, invoice, "INVOICE_VOIDED", request);
        outbox.record(principal.organizationId(), "INVOICE_VOIDED", "INVOICE", invoiceId, Map.of("invoiceId", invoiceId, "projectId", invoice.getProjectId()));
        return internalResponse(invoice);
    }

    @Transactional(readOnly = true)
    public PageResult<InvoiceSummaryResponse> clientList(ClientPrincipal principal, UUID projectId, int page, int size) {
        var access = clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        var pageable = PageRequest.of(Math.max(0, page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var visibleStatuses = java.util.EnumSet.complementOf(java.util.EnumSet.of(InvoiceStatus.DRAFT, InvoiceStatus.VOID, InvoiceStatus.CANCELLED));
        Page<Invoice> result = invoices.findAllByOrganizationIdAndProjectIdAndStatusIn(principal.organizationId(), projectId, visibleStatuses, pageable);
        return new PageResult<>(result.getContent().stream().map(InvoiceService::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ClientInvoiceResponse clientDetail(ClientPrincipal principal, UUID projectId, UUID invoiceId) {
        clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        Invoice invoice = require(principal.organizationId(), invoiceId);
        if (!projectId.equals(invoice.getProjectId()) || !clientVisible(invoice)) throw notFound();
        return clientResponse(invoice);
    }

    @Transactional
    public ClientInvoiceResponse clientMarkViewed(ClientPrincipal principal, UUID projectId, UUID invoiceId, HttpServletRequest request) {
        clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        Invoice invoice = requireForUpdate(principal.organizationId(), invoiceId);
        if (!projectId.equals(invoice.getProjectId()) || !clientVisible(invoice)) throw notFound();
        InvoiceStatus before = invoice.getStatus();
        invoice.markViewed(principal.userId(), clock.instant());
        if (invoice.getStatus() != before) {
            invoices.saveAndFlush(invoice);
            activity.recordClient(principal.organizationId(), invoice.getClientId(), projectId, principal.userId(), "INVOICE_VIEWED", "INVOICE",
                    invoiceId, "Invoice viewed", Map.of("invoiceNumber", invoice.getInvoiceNumber()));
            audit.recordClient(principal.organizationId(), principal.userId(), "INVOICE_VIEWED", "INVOICE", invoiceId,
                    Map.of("status", before), Map.of("status", invoice.getStatus()), request);
        }
        return clientResponse(invoice);
    }

    Invoice require(UUID organizationId, UUID invoiceId) {
        return invoices.findByOrganizationIdAndId(organizationId, invoiceId).orElseThrow(InvoiceService::notFound);
    }

    private Invoice requireForUpdate(UUID organizationId, UUID invoiceId) {
        return invoices.findForUpdate(organizationId, invoiceId).orElseThrow(InvoiceService::notFound);
    }

    private InvoiceConfig resolveConfig(UUID organizationId, CreateInvoiceRequest request, UUID projectId) {
        if (request.stepId() == null) {
            if (request.paymentPolicy() == null || request.currency() == null) throw invalid("paymentPolicy and currency are required for standalone invoices.");
            return new InvoiceConfig(null, null, request.paymentPolicy(), normalizeCurrency(request.currency()), request.requiredAmountMinor());
        }
        var step = steps.requireForProject(organizationId, projectId, request.stepId());
        if (step.stepType() != WorkflowStepType.PAYMENT) throw invalid("The selected workflow step is not a payment requirement.");
        JsonNode cfg = step.configuration();
        PaymentPolicy policy;
        try { policy = PaymentPolicy.valueOf(cfg.path("paymentPolicy").asText("").toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_STEP_CONFIGURATION_INVALID", "The payment workflow step has invalid configuration."); }
        String configuredCurrency = cfg.hasNonNull("currency") ? normalizeCurrency(cfg.get("currency").asText()) : null;
        String currency = configuredCurrency != null ? configuredCurrency : normalizeCurrency(request.currency());
        if (request.paymentPolicy() != null && request.paymentPolicy() != policy) throw invalid("Invoice payment policy must match the workflow requirement.");
        if (request.currency() != null && configuredCurrency != null && !normalizeCurrency(request.currency()).equals(configuredCurrency)) throw invalid("Invoice currency must match the workflow requirement.");
        Long configuredRequired = cfg.hasNonNull("requiredAmountMinor") ? cfg.get("requiredAmountMinor").longValue() : null;
        Long required = configuredRequired != null ? configuredRequired : request.requiredAmountMinor();
        if (request.requiredAmountMinor() != null && configuredRequired != null && !request.requiredAmountMinor().equals(configuredRequired)) throw invalid("Invoice payment threshold must match the workflow requirement.");
        return new InvoiceConfig(step.onboardingId(), step.id(), policy, currency, required);
    }

    private static List<InvoiceItem> prepareItems(UUID organizationId, UUID invoiceId, List<InvoiceItemRequest> values, UUID actorId, Instant now) {
        if (values == null) return List.of();
        return java.util.stream.IntStream.range(0, values.size()).mapToObj(index -> {
            InvoiceItemRequest item = values.get(index);
            return new InvoiceItem(UUID.randomUUID(), organizationId, invoiceId == null ? UUID.randomUUID() : invoiceId,
                    item.description(), item.quantity(), item.unitAmountMinor(), index, actorId, now);
        }).toList();
    }

    private boolean sameCreation(Invoice existing, UUID projectId, InvoiceConfig config, CreateInvoiceRequest request,
                                 long subtotal, long total, long required, List<InvoiceItem> prepared) {
        if (!existing.getProjectId().equals(projectId) || !Objects.equals(existing.getStepInstanceId(), config.stepId())
                || existing.getPaymentPolicy() != config.paymentPolicy() || !existing.getCurrency().equals(config.currency())
                || existing.getSubtotalMinor() != subtotal || existing.getTaxMinor() != request.taxMinor() || existing.getTotalMinor() != total
                || existing.getRequiredAmountMinor() != required || !Objects.equals(existing.getMemo(), normalizeOptional(request.memo()))
                || !Objects.equals(existing.getDueAt(), request.dueAt())) return false;
        List<InvoiceItem> persisted = items.findAllByOrganizationIdAndInvoiceIdOrderByDisplayOrderAscIdAsc(existing.getOrganizationId(), existing.getId());
        if (persisted.size() != prepared.size()) return false;
        for (int i = 0; i < persisted.size(); i++) {
            InvoiceItem a = persisted.get(i), b = prepared.get(i);
            if (!a.getDescription().equals(b.getDescription()) || a.getQuantity().compareTo(b.getQuantity()) != 0
                    || a.getUnitAmountMinor() != b.getUnitAmountMinor() || a.getLineTotalMinor() != b.getLineTotalMinor()) return false;
        }
        return true;
    }

    private InvoiceResponse internalResponse(Invoice invoice) {
        List<InvoiceItemResponse> itemValues = items.findAllByOrganizationIdAndInvoiceIdOrderByDisplayOrderAscIdAsc(invoice.getOrganizationId(), invoice.getId()).stream().map(InvoiceService::itemResponse).toList();
        List<PaymentResponse> paymentValues = payments.findAllByOrganizationIdAndInvoiceIdOrderByCreatedAtDescIdDesc(invoice.getOrganizationId(), invoice.getId()).stream().map(InvoiceService::paymentResponse).toList();
        List<PaymentTransactionResponse> transactionValues = transactions.findAllByOrganizationIdAndInvoiceIdOrderByOccurredAtAscIdAsc(invoice.getOrganizationId(), invoice.getId()).stream().map(InvoiceService::transactionResponse).toList();
        List<RefundResponse> refundValues = refunds.findAllByOrganizationIdAndInvoiceIdOrderByRequestedAtDescIdDesc(invoice.getOrganizationId(), invoice.getId()).stream().map(InvoiceService::refundResponse).toList();
        return new InvoiceResponse(invoice.getId(), invoice.getProjectId(), invoice.getClientId(), invoice.getOnboardingId(), invoice.getStepInstanceId(),
                invoice.getInvoiceNumber(), invoice.getPaymentPolicy(), invoice.getCurrency(), invoice.getStatus(), invoice.getSubtotalMinor(), invoice.getTaxMinor(),
                invoice.getTotalMinor(), invoice.getRequiredAmountMinor(), invoice.getAmountPaidMinor(), invoice.getAmountRefundedMinor(), invoice.getBalanceDueMinor(),
                invoice.getMemo(), invoice.getDueAt(), invoice.getSentAt(), invoice.getViewedAt(), invoice.getPaidAt(), invoice.getCreatedAt(), invoice.getVersion(),
                itemValues, paymentValues, transactionValues, refundValues);
    }

    private ClientInvoiceResponse clientResponse(Invoice invoice) {
        List<InvoiceItemResponse> itemValues = items.findAllByOrganizationIdAndInvoiceIdOrderByDisplayOrderAscIdAsc(invoice.getOrganizationId(), invoice.getId()).stream().map(InvoiceService::itemResponse).toList();
        List<ClientPaymentResponse> paymentValues = payments.findAllByOrganizationIdAndInvoiceIdOrderByCreatedAtDescIdDesc(invoice.getOrganizationId(), invoice.getId()).stream()
                .map(p -> new ClientPaymentResponse(p.getId(), p.getRequestedAmountMinor(), p.getCapturedAmountMinor(), p.getRefundedAmountMinor(), p.getCurrency(), p.getStatus(), p.getCreatedAt())).toList();
        return new ClientInvoiceResponse(invoice.getId(), invoice.getProjectId(), invoice.getInvoiceNumber(), invoice.getPaymentPolicy(), invoice.getCurrency(), invoice.getStatus(),
                invoice.getSubtotalMinor(), invoice.getTaxMinor(), invoice.getTotalMinor(), invoice.getRequiredAmountMinor(), invoice.getAmountPaidMinor(), invoice.getAmountRefundedMinor(),
                invoice.getBalanceDueMinor(), invoice.getMemo(), invoice.getDueAt(), invoice.getSentAt(), invoice.getViewedAt(), invoice.getPaidAt(), itemValues, paymentValues);
    }

    private void recordInternal(TenantPrincipal principal, Invoice invoice, String action, HttpServletRequest request) {
        activity.record(principal.organizationId(), invoice.getClientId(), invoice.getProjectId(), principal.userId(), action, "INVOICE", invoice.getId(),
                action.replace('_', ' ').toLowerCase(Locale.ROOT), Map.of("invoiceNumber", invoice.getInvoiceNumber(), "status", invoice.getStatus()));
        audit.record(principal.organizationId(), principal.userId(), action, "INVOICE", invoice.getId(), null, Map.of("status", invoice.getStatus()), request);
    }

    public static InvoiceSummaryResponse summary(Invoice i) { return new InvoiceSummaryResponse(i.getId(), i.getProjectId(), i.getInvoiceNumber(), i.getPaymentPolicy(), i.getCurrency(), i.getStatus(), i.getTotalMinor(), i.getRequiredAmountMinor(), i.getAmountPaidMinor(), i.getAmountRefundedMinor(), i.getBalanceDueMinor(), i.getDueAt(), i.getCreatedAt(), i.getVersion()); }
    public static InvoiceItemResponse itemResponse(InvoiceItem i) { return new InvoiceItemResponse(i.getId(), i.getDescription(), i.getQuantity(), i.getUnitAmountMinor(), i.getLineTotalMinor(), i.getDisplayOrder()); }
    public static PaymentResponse paymentResponse(Payment p) { return new PaymentResponse(p.getId(), p.getProvider(), p.getProviderPaymentId(), p.getRequestedAmountMinor(), p.getCapturedAmountMinor(), p.getRefundedAmountMinor(), p.getCurrency(), p.getStatus(), p.getCheckoutUrl(), p.getSessionExpiresAt(), p.getFailureReason(), p.getCreatedAt(), p.getVersion()); }
    public static PaymentTransactionResponse transactionResponse(PaymentTransaction t) { return new PaymentTransactionResponse(t.getId(), t.getPaymentId(), t.getTransactionType(), t.getAmountMinor(), t.getCurrency(), t.getReason(), t.getOccurredAt()); }
    public static RefundResponse refundResponse(Refund r) { return new RefundResponse(r.getId(), r.getPaymentId(), r.getProvider(), r.getProviderRefundId(), r.getAmountMinor(), r.getCurrency(), r.getStatus(), r.getReason(), r.getFailureReason(), r.getRequestedAt(), r.getCompletedAt(), r.getVersion()); }

    private static boolean clientVisible(Invoice invoice) { return invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.VOID && invoice.getStatus() != InvoiceStatus.CANCELLED; }
    private static int safeSize(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }
    private static String invoiceNumber(Instant now) { return "INV-" + INVOICE_MONTH.format(now) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT); }
    private static String normalizeCurrency(String value) { if (value == null || value.isBlank()) throw invalid("Currency is required."); String n=value.trim().toUpperCase(Locale.ROOT); if(!n.matches("[A-Z]{3}")) throw invalid("Currency must be a three-letter code."); return n; }
    private static String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizeIdempotencyKey(String value) { if(value==null||!value.matches("[A-Za-z0-9._:-]{8,128}")) throw new ApiException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_INVALID","A valid Idempotency-Key is required."); return value; }
    private static void requireVersion(Invoice invoice,long expected){if(invoice.getVersion()!=expected)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The invoice changed. Refresh and try again.");}
    private static ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVOICE_INVALID",message);}
    private static ApiException state(String message){return new ApiException(HttpStatus.CONFLICT,"INVOICE_STATE_INVALID",message);}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested invoice was not found.");}

    private record InvoiceConfig(UUID onboardingId, UUID stepId, PaymentPolicy paymentPolicy, String currency, Long requiredAmountMinor) {}
    public record PageResult<T>(List<T> items,int page,int size,long totalElements,int totalPages){public PageResult{items=List.copyOf(items);}}
}
