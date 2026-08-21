package com.brainserve.onboarding.billing.api.response;
import java.math.BigDecimal;import java.util.UUID;
public record InvoiceItemResponse(UUID id,String description,BigDecimal quantity,long unitAmountMinor,long lineTotalMinor,int displayOrder) {}
