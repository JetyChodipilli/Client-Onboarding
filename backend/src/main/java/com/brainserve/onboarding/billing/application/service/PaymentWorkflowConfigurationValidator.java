package com.brainserve.onboarding.billing.application.service;

import com.brainserve.onboarding.billing.domain.model.PaymentPolicy;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.workflow.application.service.WorkflowStepConfigurationValidator;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentWorkflowConfigurationValidator implements WorkflowStepConfigurationValidator {
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;

    @Override
    public boolean supports(WorkflowStepType stepType) {
        return stepType == WorkflowStepType.PAYMENT;
    }

    @Override
    public void validate(UUID organizationId, String stepKey, JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            throw invalid(stepKey, "PAYMENT steps require an object configuration.");
        }
        PaymentPolicy policy;
        try {
            JsonNode raw = configuration.get("paymentPolicy");
            policy = PaymentPolicy.valueOf(raw == null ? "" : raw.asText().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw invalid(stepKey, "paymentPolicy must be FULL, DEPOSIT, MILESTONE, MANUAL or NO_PAYMENT_REQUIRED.");
        }
        JsonNode currency = configuration.get("currency");
        if (currency != null && !currency.isNull()) {
            String value = currency.asText("").trim().toUpperCase(Locale.ROOT);
            if (!value.matches("[A-Z]{3}")) throw invalid(stepKey, "currency must be a three-letter code.");
        }
        JsonNode required = configuration.get("requiredAmountMinor");
        Long amount = null;
        if (required != null && !required.isNull()) {
            if (!required.canConvertToLong()) throw invalid(stepKey, "requiredAmountMinor must be an integer minor-unit amount.");
            amount = required.longValue();
            if (amount < 0 || amount > MAX_AMOUNT_MINOR) throw invalid(stepKey, "requiredAmountMinor is outside the supported range.");
        }
        if (policy == PaymentPolicy.NO_PAYMENT_REQUIRED && amount != null && amount != 0) {
            throw invalid(stepKey, "NO_PAYMENT_REQUIRED must use a zero required amount.");
        }
        if ((policy == PaymentPolicy.DEPOSIT || policy == PaymentPolicy.MILESTONE) && (amount == null || amount <= 0)) {
            throw invalid(stepKey, policy + " requires a positive requiredAmountMinor threshold.");
        }
    }

    private static ApiException invalid(String stepKey, String message) {
        return new ApiException(HttpStatus.CONFLICT, "WORKFLOW_PAYMENT_CONFIGURATION_INVALID", "Step " + stepKey + ": " + message);
    }
}
