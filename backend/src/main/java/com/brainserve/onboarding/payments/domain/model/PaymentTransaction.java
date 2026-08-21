package com.brainserve.onboarding.payments.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="payment_transactions", schema="client_onboarding")
public class PaymentTransaction {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="invoice_id", nullable=false) private UUID invoiceId;
    @Column(name="payment_id", nullable=false) private UUID paymentId;
    @Column(nullable=false,length=40) private String provider;
    @Column(name="provider_event_id",length=180) private String providerEventId;
    @Column(name="provider_transaction_id",length=180) private String providerTransactionId;
    @Enumerated(EnumType.STRING) @Column(name="transaction_type",nullable=false,length=32) private PaymentTransactionType transactionType;
    @Column(name="amount_minor",nullable=false) private long amountMinor;
    @Column(nullable=false,length=3) private String currency;
    @Column(length=2000) private String reason;
    @Enumerated(EnumType.STRING) @Column(name="actor_type",nullable=false,length=16) private PaymentActorType actorType;
    @Column(name="actor_user_id") private UUID actorUserId;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected PaymentTransaction(){}
    public PaymentTransaction(UUID id,UUID organizationId,UUID invoiceId,UUID paymentId,String provider,String providerEventId,
                              String providerTransactionId,PaymentTransactionType type,long amountMinor,String currency,String reason,
                              PaymentActorType actorType,UUID actorUserId,Instant occurredAt,Instant createdAt){
        this.id=id;this.organizationId=organizationId;this.invoiceId=invoiceId;this.paymentId=paymentId;this.provider=provider;
        this.providerEventId=providerEventId;this.providerTransactionId=providerTransactionId;this.transactionType=type;
        if(amountMinor<0)throw new IllegalArgumentException("Transaction amount cannot be negative");this.amountMinor=amountMinor;
        this.currency=currency;this.reason=reason;this.actorType=actorType;this.actorUserId=actorUserId;this.occurredAt=occurredAt;this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getInvoiceId(){return invoiceId;} public UUID getPaymentId(){return paymentId;}
    public String getProvider(){return provider;} public String getProviderEventId(){return providerEventId;} public String getProviderTransactionId(){return providerTransactionId;}
    public PaymentTransactionType getTransactionType(){return transactionType;} public long getAmountMinor(){return amountMinor;} public String getCurrency(){return currency;}
    public String getReason(){return reason;} public PaymentActorType getActorType(){return actorType;} public UUID getActorUserId(){return actorUserId;} public Instant getOccurredAt(){return occurredAt;} public Instant getCreatedAt(){return createdAt;}
}
