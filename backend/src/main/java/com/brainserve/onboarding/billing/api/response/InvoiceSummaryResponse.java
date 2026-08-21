package com.brainserve.onboarding.billing.api.response;
import com.brainserve.onboarding.billing.domain.model.*;import java.time.Instant;import java.util.UUID;
public record InvoiceSummaryResponse(UUID id,UUID projectId,String invoiceNumber,PaymentPolicy paymentPolicy,String currency,InvoiceStatus status,long totalMinor,long requiredAmountMinor,long amountPaidMinor,long amountRefundedMinor,long balanceDueMinor,Instant dueAt,Instant createdAt,long version) {}
