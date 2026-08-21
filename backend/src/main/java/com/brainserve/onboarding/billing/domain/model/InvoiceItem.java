package com.brainserve.onboarding.billing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="invoice_items", schema="client_onboarding")
public class InvoiceItem {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="invoice_id", nullable=false) private UUID invoiceId;
    @Column(nullable=false, length=500) private String description;
    @Column(nullable=false, precision=12, scale=3) private BigDecimal quantity;
    @Column(name="unit_amount_minor", nullable=false) private long unitAmountMinor;
    @Column(name="line_total_minor", nullable=false) private long lineTotalMinor;
    @Column(name="display_order", nullable=false) private int displayOrder;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="created_by", nullable=false) private UUID createdBy;

    protected InvoiceItem() {}
    public InvoiceItem(UUID id, UUID organizationId, UUID invoiceId, String description, BigDecimal quantity,
                       long unitAmountMinor, int displayOrder, UUID actorId, Instant now) {
        this.id=id; this.organizationId=organizationId; this.invoiceId=invoiceId;
        if(description==null||description.isBlank()) throw new IllegalArgumentException("Invoice item description is required");
        this.description=description.trim(); if(this.description.length()>500) throw new IllegalArgumentException("Invoice item description is too long");
        if(quantity==null||quantity.signum()<=0) throw new IllegalArgumentException("Invoice item quantity must be positive");
        this.quantity=quantity.setScale(3, RoundingMode.UNNECESSARY);
        if(unitAmountMinor<0) throw new IllegalArgumentException("Unit amount cannot be negative");
        this.unitAmountMinor=unitAmountMinor;
        this.lineTotalMinor=this.quantity.multiply(BigDecimal.valueOf(unitAmountMinor)).setScale(0,RoundingMode.HALF_UP).longValueExact();
        this.displayOrder=displayOrder; this.createdBy=actorId; this.createdAt=now;
    }
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getInvoiceId(){return invoiceId;}
    public String getDescription(){return description;} public BigDecimal getQuantity(){return quantity;} public long getUnitAmountMinor(){return unitAmountMinor;}
    public long getLineTotalMinor(){return lineTotalMinor;} public int getDisplayOrder(){return displayOrder;} public Instant getCreatedAt(){return createdAt;}
}
