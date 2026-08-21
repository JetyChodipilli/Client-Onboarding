package com.brainserve.onboarding.reporting.api.response;

public record FinancialCurrencyResponse(
        String currency,
        long invoicesSent,
        long invoicesPaid,
        long invoicedMinor,
        long collectedMinor,
        long refundedMinor) {
}
