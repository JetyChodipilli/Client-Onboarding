package com.brainserve.clientonboarding.billing.domain.model;

import static com.brainserve.clientonboarding.billing.domain.model.BillingModels.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InvoicePolicy {
    public static final long MAX_MINOR = 100_000_000_000L;
    private InvoicePolicy() { }
    public static Totals calculate(List<ItemInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 50) throw new IllegalArgumentException("Provide 1 to 50 invoice items.");
        var items = new ArrayList<Item>(); long subtotal = 0, tax = 0;
        for (var line : inputs) {
            if (line == null || line.description() == null || line.description().isBlank() || line.description().length() > 500
                    || line.quantity() < 1 || line.quantity() > 10000 || line.unitAmountMinor() < 0 || line.unitAmountMinor() > MAX_MINOR
                    || line.taxBasisPoints() < 0 || line.taxBasisPoints() > 10000) throw new IllegalArgumentException("Check the item description, quantity, amount and tax rate.");
            long base = Math.multiplyExact(line.quantity(), line.unitAmountMinor());
            if (base > MAX_MINOR) throw new IllegalArgumentException("The invoice exceeds the amount limit.");
            long lineTax = (Math.multiplyExact(base, line.taxBasisPoints()) + 5000) / 10000;
            subtotal = Math.addExact(subtotal, base); tax = Math.addExact(tax, lineTax);
            if (subtotal + tax > MAX_MINOR) throw new IllegalArgumentException("The invoice exceeds the amount limit.");
            items.add(new Item(items.size(), line.description().trim(), line.quantity(), line.unitAmountMinor(), line.taxBasisPoints(), base, lineTax, base + lineTax));
        }
        return new Totals(List.copyOf(items), subtotal, tax, subtotal + tax);
    }
    public static Policy policy(Map<String, Object> configuration) {
        try { return Policy.valueOf(String.valueOf(configuration.get("paymentPolicy"))); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Select a payment policy."); }
    }
    public static long number(Map<String, Object> configuration, String key) {
        Object value = configuration.get(key);
        if (!(value instanceof Number n) || n.doubleValue() != n.longValue()) throw new IllegalArgumentException("Provide a whole-number " + key + ".");
        return n.longValue();
    }
    public static void validate(Map<String, Object> configuration) {
        Policy policy = policy(configuration);
        if (policy == Policy.DEPOSIT && (number(configuration,"depositPercent") < 1 || number(configuration,"depositPercent") > 99))
            throw new IllegalArgumentException("Deposit percentage must be between 1 and 99.");
        if (policy == Policy.MILESTONE && (number(configuration,"milestoneAmountMinor") < 1 || number(configuration,"milestoneAmountMinor") > MAX_MINOR))
            throw new IllegalArgumentException("Provide a positive milestone amount within the invoice limit.");
    }
    public static long threshold(Map<String, Object> configuration, long total) {
        validate(configuration); Policy policy = policy(configuration);
        if (policy != Policy.NO_PAYMENT_REQUIRED && total < 100) throw new IllegalArgumentException("An invoice requiring payment must total at least INR 1.00.");
        long threshold = switch (policy) {
            case DEPOSIT -> (total * number(configuration,"depositPercent") + 99) / 100;
            case MILESTONE -> number(configuration,"milestoneAmountMinor");
            case NO_PAYMENT_REQUIRED -> 0;
            default -> total;
        };
        if (threshold > total) throw new IllegalArgumentException("The milestone exceeds the invoice total.");
        return threshold;
    }
    public static Status status(Invoice invoice, long captured, long refunded) {
        if (invoice.closed() || invoice.status() == Status.DRAFT) return invoice.status();
        if (captured - refunded >= invoice.totalMinor()) return Status.PAID;
        if (captured > 0 && refunded == captured) return Status.REFUNDED;
        if (refunded > 0) return Status.PARTIALLY_REFUNDED;
        if (captured > 0) return Status.PARTIALLY_PAID;
        return invoice.viewedAt() == null ? Status.SENT : Status.VIEWED;
    }
}
