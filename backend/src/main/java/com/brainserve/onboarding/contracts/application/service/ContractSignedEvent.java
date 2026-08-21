package com.brainserve.onboarding.contracts.application.service;
import java.util.UUID;
public record ContractSignedEvent(UUID organizationId, UUID contractId, UUID onboardingId, UUID stepId) {}
