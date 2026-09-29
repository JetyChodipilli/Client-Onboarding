package com.brainserve.clientonboarding.billing.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingModels {
    private BillingModels() { }
    public enum Policy { FULL, DEPOSIT, MILESTONE, MANUAL, NO_PAYMENT_REQUIRED }
    public enum Status { DRAFT, SENT, VIEWED, PARTIALLY_PAID, PAID, OVERDUE, VOID, CANCELLED, REFUNDED, PARTIALLY_REFUNDED }
    public record ItemInput(String description, int quantity, long unitAmountMinor, int taxBasisPoints) { }
    public record Item(int position, String description, int quantity, long unitAmountMinor, int taxBasisPoints,
                       long subtotalMinor, long taxMinor, long totalMinor) { }
    public record Totals(List<Item> items, long subtotalMinor, long taxMinor, long totalMinor) { }
    public record Invoice(UUID id, UUID organizationId, UUID projectId, UUID stepId, String invoiceNumber,
                          String currency, Policy policy, long subtotalMinor, long taxMinor, long totalMinor,
                          long thresholdMinor, long capturedMinor, long refundedMinor, long reservedMinor,
                          Status status, LocalDate dueDate, String note, Instant sentAt, Instant viewedAt,
                          Instant createdAt, long version) {
        public long paidMinor() { return capturedMinor - refundedMinor; }
        public long balanceMinor() { return totalMinor - paidMinor(); }
        public boolean closed() { return status == Status.VOID || status == Status.CANCELLED; }
        public boolean satisfied() { return status != Status.DRAFT && !closed() && paidMinor() >= thresholdMinor; }
    }
}
