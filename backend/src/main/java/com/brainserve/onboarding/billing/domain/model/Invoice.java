package com.brainserve.onboarding.billing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "invoices", schema = "client_onboarding")
public class Invoice {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="project_id", nullable=false) private UUID projectId;
    @Column(name="client_id", nullable=false) private UUID clientId;
    @Column(name="onboarding_id") private UUID onboardingId;
    @Column(name="step_instance_id") private UUID stepInstanceId;
    @Column(name="invoice_number", nullable=false, length=64) private String invoiceNumber;
    @Enumerated(EnumType.STRING) @Column(name="payment_policy", nullable=false, length=32) private PaymentPolicy paymentPolicy;
    @Column(nullable=false, length=3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private InvoiceStatus status;
    @Column(name="subtotal_minor", nullable=false) private long subtotalMinor;
    @Column(name="tax_minor", nullable=false) private long taxMinor;
    @Column(name="total_minor", nullable=false) private long totalMinor;
    @Column(name="required_amount_minor", nullable=false) private long requiredAmountMinor;
    @Column(name="amount_paid_minor", nullable=false) private long amountPaidMinor;
    @Column(name="amount_refunded_minor", nullable=false) private long amountRefundedMinor;
    @Column(name="balance_due_minor", nullable=false) private long balanceDueMinor;
    @Column(length=2000) private String memo;
    @Column(name="due_at") private Instant dueAt;
    @Column(name="sent_at") private Instant sentAt;
    @Column(name="viewed_at") private Instant viewedAt;
    @Column(name="paid_at") private Instant paidAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="creation_idempotency_key", nullable=false, length=128) private String creationIdempotencyKey;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(name="updated_by") private UUID updatedBy;
    @Enumerated(EnumType.STRING) @Column(name="updated_by_type", nullable=false, length=16) private BillingActorType updatedByType;
    @Version private long version;

    protected Invoice() {}

    public Invoice(UUID id, UUID organizationId, UUID projectId, UUID clientId, UUID onboardingId, UUID stepInstanceId,
                   String invoiceNumber, PaymentPolicy paymentPolicy, String currency, long subtotalMinor, long taxMinor,
                   long requiredAmountMinor, String memo, Instant dueAt, String creationIdempotencyKey, UUID actorId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.clientId = clientId;
        this.onboardingId = onboardingId;
        this.stepInstanceId = stepInstanceId;
        this.invoiceNumber = requiredText(invoiceNumber, "Invoice number", 64);
        this.paymentPolicy = java.util.Objects.requireNonNull(paymentPolicy, "paymentPolicy");
        this.currency = normalizeCurrency(currency);
        if (subtotalMinor < 0 || taxMinor < 0) throw new IllegalArgumentException("Invoice amounts cannot be negative");
        this.subtotalMinor = subtotalMinor;
        this.taxMinor = taxMinor;
        this.totalMinor = Math.addExact(subtotalMinor, taxMinor);
        if (requiredAmountMinor < 0 || requiredAmountMinor > this.totalMinor) throw new IllegalArgumentException("Required payment amount is invalid");
        if (paymentPolicy == PaymentPolicy.NO_PAYMENT_REQUIRED && (this.totalMinor != 0 || requiredAmountMinor != 0)) {
            throw new IllegalArgumentException("NO_PAYMENT_REQUIRED invoices must have zero total and zero required amount");
        }
        this.requiredAmountMinor = requiredAmountMinor;
        this.amountPaidMinor = 0;
        this.amountRefundedMinor = 0;
        this.balanceDueMinor = this.totalMinor;
        this.memo = optional(memo, 2000);
        this.dueAt = dueAt;
        this.creationIdempotencyKey = requiredText(creationIdempotencyKey, "Idempotency key", 128);
        this.status = paymentPolicy == PaymentPolicy.NO_PAYMENT_REQUIRED ? InvoiceStatus.PAID : InvoiceStatus.DRAFT;
        this.paidAt = paymentPolicy == PaymentPolicy.NO_PAYMENT_REQUIRED ? now : null;
        this.createdAt = now;
        this.createdBy = actorId;
        touchInternal(actorId, now);
    }

    public void send(UUID actorId, Instant now) {
        if (status != InvoiceStatus.DRAFT) throw new IllegalStateException("Only draft invoices can be sent");
        if (totalMinor <= 0) throw new IllegalStateException("A payable invoice must have a positive total");
        status = InvoiceStatus.SENT;
        sentAt = now;
        touchInternal(actorId, now);
    }

    public void markViewed(UUID actorId, Instant now) {
        if (status == InvoiceStatus.SENT) {
            status = InvoiceStatus.VIEWED;
            viewedAt = now;
            touch(BillingActorType.CLIENT, actorId, now);
        }
    }

    public void cancel(UUID actorId, Instant now) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.PARTIALLY_PAID || status == InvoiceStatus.PARTIALLY_REFUNDED || status == InvoiceStatus.REFUNDED) {
            throw new IllegalStateException("Invoices with payment history cannot be cancelled");
        }
        if (status == InvoiceStatus.CANCELLED || status == InvoiceStatus.VOID) throw new IllegalStateException("Invoice is already closed");
        status = InvoiceStatus.CANCELLED;
        cancelledAt = now;
        touchInternal(actorId, now);
    }

    public void voidInvoice(UUID actorId, Instant now) {
        if (amountPaidMinor != 0 || amountRefundedMinor != 0) throw new IllegalStateException("Invoices with payment history cannot be voided");
        if (status == InvoiceStatus.CANCELLED || status == InvoiceStatus.VOID || status == InvoiceStatus.PAID) throw new IllegalStateException("Invoice cannot be voided from its current state");
        status = InvoiceStatus.VOID;
        cancelledAt = now;
        touchInternal(actorId, now);
    }

    public void reconcile(long grossCapturedMinor, long refundedMinor, BillingActorType actorType, UUID actorId, Instant now) {
        if (grossCapturedMinor < 0 || refundedMinor < 0 || refundedMinor > grossCapturedMinor) throw new IllegalArgumentException("Payment ledger totals are invalid");
        // Provider-confirmed ledger facts are authoritative even if a late webhook arrives after an internal close.
        // Reconciliation therefore restores the financial state instead of discarding a real capture/refund.
        this.amountPaidMinor = grossCapturedMinor;
        this.amountRefundedMinor = refundedMinor;
        long net = Math.max(0L, grossCapturedMinor - refundedMinor);
        this.balanceDueMinor = Math.max(0L, totalMinor - net);
        if (paymentPolicy == PaymentPolicy.NO_PAYMENT_REQUIRED || totalMinor == 0) {
            status = InvoiceStatus.PAID;
            paidAt = paidAt == null ? now : paidAt;
        } else if (refundedMinor > 0 && net == 0) {
            status = InvoiceStatus.REFUNDED;
            paidAt = null;
        } else if (refundedMinor > 0) {
            status = InvoiceStatus.PARTIALLY_REFUNDED;
            if (net >= totalMinor && paidAt == null) paidAt = now;
        } else if (net >= totalMinor) {
            status = InvoiceStatus.PAID;
            paidAt = paidAt == null ? now : paidAt;
        } else if (net > 0) {
            status = InvoiceStatus.PARTIALLY_PAID;
            paidAt = null;
        } else if (dueAt != null && now.isAfter(dueAt) && status != InvoiceStatus.DRAFT) {
            status = InvoiceStatus.OVERDUE;
            paidAt = null;
        } else if (status != InvoiceStatus.DRAFT) {
            status = viewedAt == null ? InvoiceStatus.SENT : InvoiceStatus.VIEWED;
            paidAt = null;
        }
        touch(actorType, actorId, now);
    }

    public void markOverdue(Instant now) {
        if (dueAt != null && now.isAfter(dueAt) && balanceDueMinor > 0 && (status == InvoiceStatus.SENT || status == InvoiceStatus.VIEWED)) {
            status = InvoiceStatus.OVERDUE;
            touch(BillingActorType.SYSTEM, null, now);
        }
    }

    public boolean paymentRequirementSatisfied() {
        return paymentPolicy == PaymentPolicy.NO_PAYMENT_REQUIRED || Math.max(0L, amountPaidMinor - amountRefundedMinor) >= requiredAmountMinor;
    }

    private void touchInternal(UUID actorId, Instant now) { touch(BillingActorType.INTERNAL, actorId, now); }
    private void touch(BillingActorType type, UUID actorId, Instant now) {
        this.updatedAt = now;
        this.updatedByType = type;
        this.updatedBy = (type == BillingActorType.INTERNAL || type == BillingActorType.CLIENT) ? java.util.Objects.requireNonNull(actorId, "actorId") : null;
    }
    private static String normalizeCurrency(String value) {
        String normalized = requiredText(value, "Currency", 3).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be an ISO-style 3-letter code");
        return normalized;
    }
    private static String requiredText(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Text is too long");
        return normalized;
    }

    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getProjectId(){return projectId;}
    public UUID getClientId(){return clientId;} public UUID getOnboardingId(){return onboardingId;} public UUID getStepInstanceId(){return stepInstanceId;}
    public String getInvoiceNumber(){return invoiceNumber;} public PaymentPolicy getPaymentPolicy(){return paymentPolicy;} public String getCurrency(){return currency;}
    public InvoiceStatus getStatus(){return status;} public long getSubtotalMinor(){return subtotalMinor;} public long getTaxMinor(){return taxMinor;}
    public long getTotalMinor(){return totalMinor;} public long getRequiredAmountMinor(){return requiredAmountMinor;} public long getAmountPaidMinor(){return amountPaidMinor;}
    public long getAmountRefundedMinor(){return amountRefundedMinor;} public long getBalanceDueMinor(){return balanceDueMinor;} public String getMemo(){return memo;}
    public Instant getDueAt(){return dueAt;} public Instant getSentAt(){return sentAt;} public Instant getViewedAt(){return viewedAt;} public Instant getPaidAt(){return paidAt;}
    public Instant getCancelledAt(){return cancelledAt;} public String getCreationIdempotencyKey(){return creationIdempotencyKey;} public Instant getCreatedAt(){return createdAt;}
    public UUID getCreatedBy(){return createdBy;} public Instant getUpdatedAt(){return updatedAt;} public UUID getUpdatedBy(){return updatedBy;} public BillingActorType getUpdatedByType(){return updatedByType;}
    public long getVersion(){return version;}
}
