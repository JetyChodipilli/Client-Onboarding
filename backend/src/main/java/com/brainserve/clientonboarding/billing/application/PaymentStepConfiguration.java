package com.brainserve.clientonboarding.billing.application;

import com.brainserve.clientonboarding.billing.domain.model.InvoicePolicy;
import com.brainserve.clientonboarding.workflow.application.StepConfigurationValidator;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentStepConfiguration implements StepConfigurationValidator {
    public void validate(UUID org,TemplateStep step) {
        if(step.stepType()!=TemplateStep.StepType.PAYMENT)return;
        try {InvoicePolicy.validate(step.configuration());}
        catch(IllegalArgumentException e){throw BillingErrors.invalid(e.getMessage());}
        if(!step.clientVisible() || step.requiresReview() || step.allowSkip() || step.allowReopen())
            throw BillingErrors.invalid("Payment steps must be client visible, without manual review, skip or reopen. Use a separate approval step for internal review.");
    }
}
