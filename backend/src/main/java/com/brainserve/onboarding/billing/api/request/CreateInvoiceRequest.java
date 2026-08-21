package com.brainserve.onboarding.billing.api.request;

import com.brainserve.onboarding.billing.domain.model.PaymentPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID projectId,
        UUID stepId,
        PaymentPolicy paymentPolicy,
        @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @PositiveOrZero Long requiredAmountMinor,
        @PositiveOrZero long taxMinor,
        @Size(max = 2000) String memo,
        Instant dueAt,
        @Valid @Size(max = 100) List<InvoiceItemRequest> items) {
    public CreateInvoiceRequest { items = items == null ? List.of() : List.copyOf(items); }
}
