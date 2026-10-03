package com.brainserve.clientonboarding.billing.application;

import com.brainserve.clientonboarding.common.error.DomainException;
import org.springframework.http.HttpStatus;

public final class BillingErrors {
    private BillingErrors() { }
    public static DomainException missing() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    public static DomainException invalid(String message) { return new DomainException("BILLING_VALIDATION", message, HttpStatus.BAD_REQUEST); }
    public static DomainException state(String message) { return new DomainException("BILLING_STATE_CONFLICT", message, HttpStatus.CONFLICT); }
    public static DomainException conflict() { return state("This invoice changed. Refresh it and try again."); }
    public static String key(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,120}")) throw invalid("Idempotency-Key must contain 8 to 120 safe characters.");
        return value;
    }
    public static String reason(String value) {
        if (value == null || value.isBlank() || value.length() > 2000) throw invalid("Provide a reason of at most 2000 characters.");
        return value.trim();
    }
    public static void page(int page, int size) { if (page < 0 || page > 100000 || size < 1 || size > 50) throw invalid("Use a valid page and a page size from 1 to 50."); }
}
