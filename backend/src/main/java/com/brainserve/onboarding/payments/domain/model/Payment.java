package com.brainserve.onboarding.payments.domain.model;

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
@Table(name="payments", schema="client_onboarding")
public class Payment {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="invoice_id", nullable=false) private UUID invoiceId;
    @Column(name="project_id", nullable=false) private UUID projectId;
    @Column(nullable=false, length=40) private String provider;
    @Column(name="provider_payment_id", length=180) private String providerPaymentId;
    @Column(name="provider_session_id", length=180) private String providerSessionId;
    @Column(name="checkout_url", length=2000) private String checkoutUrl;
    @Column(name="session_expires_at") private Instant sessionExpiresAt;
    @Column(name="requested_amount_minor", nullable=false) private long requestedAmountMinor;
    @Column(name="captured_amount_minor", nullable=false) private long capturedAmountMinor;
    @Column(name="refunded_amount_minor", nullable=false) private long refundedAmountMinor;
    @Column(nullable=false, length=3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private PaymentStatus status;
    @Column(name="session_idempotency_key", nullable=false, length=128) private String sessionIdempotencyKey;
    @Column(name="failure_reason", length=1000) private String failureReason;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;

    protected Payment() {}
    public Payment(UUID id, UUID organizationId, UUID invoiceId, UUID projectId, String provider, long requestedAmountMinor,
                   String currency, String sessionIdempotencyKey, UUID actorId, Instant now) {
        this.id=id;this.organizationId=organizationId;this.invoiceId=invoiceId;this.projectId=projectId;
        this.provider=text(provider,"Provider",40).toUpperCase(Locale.ROOT);
        if(requestedAmountMinor<=0) throw new IllegalArgumentException("Payment amount must be positive");
        this.requestedAmountMinor=requestedAmountMinor;this.currency=currency.trim().toUpperCase(Locale.ROOT);
        if(!this.currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency is invalid");
        this.sessionIdempotencyKey=text(sessionIdempotencyKey,"Idempotency key",128);
        this.status=PaymentStatus.INITIATED;this.createdBy=actorId;this.createdAt=now;this.updatedAt=now;
    }
    public void sessionCreated(String providerPaymentId,String providerSessionId,String checkoutUrl,Instant expiresAt,Instant now){
        if(status!=PaymentStatus.INITIATED) throw new IllegalStateException("Payment session has already been created");
        this.providerPaymentId=optional(providerPaymentId,180);this.providerSessionId=text(providerSessionId,"Provider session",180);
        this.checkoutUrl=text(checkoutUrl,"Checkout URL",2000);this.sessionExpiresAt=java.util.Objects.requireNonNull(expiresAt,"expiresAt");
        if(!expiresAt.isAfter(now))throw new IllegalArgumentException("Payment session expiry must be in the future");
        this.status=PaymentStatus.PENDING;this.updatedAt=now;
    }
    public void failBeforeSession(String reason, Instant now) {
        if (status != PaymentStatus.INITIATED) throw new IllegalStateException("Payment session has already been finalized");
        this.status = PaymentStatus.FAILED;
        this.failureReason = optional(reason, 1000);
        this.updatedAt = now;
    }

    public void apply(PaymentStatus target,long captured,long refunded,String providerPaymentId,String failureReason,Instant now){
        if(captured<0||refunded<0||refunded>captured) throw new IllegalArgumentException("Payment ledger totals are invalid");
        this.providerPaymentId=providerPaymentId==null?this.providerPaymentId:optional(providerPaymentId,180);
        this.capturedAmountMinor=captured;this.refundedAmountMinor=refunded;this.status=target;
        this.failureReason=optional(failureReason,1000);this.updatedAt=now;
    }
    private static String text(String v,String l,int m){if(v==null||v.isBlank())throw new IllegalArgumentException(l+" is required");String n=v.trim();if(n.length()>m)throw new IllegalArgumentException(l+" is too long");return n;}
    private static String optional(String v,int m){if(v==null||v.isBlank())return null;String n=v.trim();return n.length()<=m?n:n.substring(0,m);}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getInvoiceId(){return invoiceId;} public UUID getProjectId(){return projectId;}
    public String getProvider(){return provider;} public String getProviderPaymentId(){return providerPaymentId;} public String getProviderSessionId(){return providerSessionId;}
    public String getCheckoutUrl(){return checkoutUrl;} public Instant getSessionExpiresAt(){return sessionExpiresAt;}
    public long getRequestedAmountMinor(){return requestedAmountMinor;} public long getCapturedAmountMinor(){return capturedAmountMinor;} public long getRefundedAmountMinor(){return refundedAmountMinor;}
    public String getCurrency(){return currency;} public PaymentStatus getStatus(){return status;} public String getSessionIdempotencyKey(){return sessionIdempotencyKey;}
    public String getFailureReason(){return failureReason;} public Instant getCreatedAt(){return createdAt;} public UUID getCreatedBy(){return createdBy;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
