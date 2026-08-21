package com.brainserve.onboarding.reporting.api.response;

import java.util.List;

public record FinancialReportResponse(
        long invoicesSent,
        long invoicesPaid,
        long invoicesOverdue,
        long invoicesPartiallyPaid,
        Double averagePaymentHours,
        List<FinancialCurrencyResponse> currencies) {
    public FinancialReportResponse {
        currencies = List.copyOf(currencies);
    }
}
