package com.brainserve.onboarding.payments.application.service;
import java.util.UUID;
public record PaymentRequirementChangedEvent(UUID organizationId, UUID invoiceId) {}
