package com.brainserve.onboarding.payments.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.application.service.WorkflowActorType;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps PAYMENT workflow requirements synchronized only after the financial ledger commits. */
@Service
public class PaymentWorkflowSyncService {
    private final InvoiceRepository invoices;
    private final OnboardingStepAccessService stepAccess;
    private final OnboardingStepCommandService commands;
    private final AuditService audit;
    private final OutboxService outbox;

    public PaymentWorkflowSyncService(InvoiceRepository invoices, OnboardingStepAccessService stepAccess,
                                      OnboardingStepCommandService commands, AuditService audit, OutboxService outbox) {
        this.invoices = invoices;
        this.stepAccess = stepAccess;
        this.commands = commands;
        this.audit = audit;
        this.outbox = outbox;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterCommit(PaymentRequirementChangedEvent event) {
        Invoice invoice = invoices.findForUpdate(event.organizationId(), event.invoiceId()).orElse(null);
        if (invoice == null || invoice.getStepInstanceId() == null || invoice.getOnboardingId() == null) return;
        var step = stepAccess.requireForProject(event.organizationId(), invoice.getProjectId(), invoice.getStepInstanceId());
        if (step.stepType() != WorkflowStepType.PAYMENT) return;

        if (invoice.paymentRequirementSatisfied()) {
            completeIfEligible(invoice, step);
        } else if (step.status() == OnboardingStepStatus.COMPLETED) {
            if (step.allowReopen()) {
                commands.transition(event.organizationId(), step.onboardingId(), step.id(), OnboardingStepStatus.IN_PROGRESS,
                        null, WorkflowActorType.SYSTEM);
                audit.recordApplication(event.organizationId(), null, "SYSTEM", "PAYMENT_REQUIREMENT_REOPENED",
                        "INVOICE", invoice.getId(), null, Map.of("stepId", step.id(), "netPaidMinor",
                                Math.max(0L, invoice.getAmountPaidMinor() - invoice.getAmountRefundedMinor()),
                                "requiredAmountMinor", invoice.getRequiredAmountMinor()));
            } else {
                outbox.record(event.organizationId(), "PAYMENT_RECONCILIATION_REQUIRED", "INVOICE", invoice.getId(),
                        Map.of("invoiceId", invoice.getId(), "stepId", step.id(), "reason", "REFUND_REDUCED_PAYMENT_BELOW_WORKFLOW_THRESHOLD"));
                audit.recordApplication(event.organizationId(), null, "SYSTEM", "PAYMENT_RECONCILIATION_REQUIRED",
                        "INVOICE", invoice.getId(), null, Map.of("stepId", step.id(), "allowReopen", false));
            }
        }
    }

    private void completeIfEligible(Invoice invoice, OnboardingStepAccessService.StepRef initial) {
        OnboardingStepStatus status = initial.status();
        if (status == OnboardingStepStatus.COMPLETED) return;
        if (status == OnboardingStepStatus.LOCKED || status == OnboardingStepStatus.CANCELLED || status == OnboardingStepStatus.SKIPPED) {
            outbox.record(invoice.getOrganizationId(), "PAYMENT_RECONCILIATION_REQUIRED", "INVOICE", invoice.getId(),
                    Map.of("invoiceId", invoice.getId(), "stepId", initial.id(), "reason", "PAYMENT_CAPTURED_WHILE_STEP_NOT_ACTIONABLE"));
            return;
        }
        // Existing snapshots created before Phase 7 may have requiresReview=true. Provider-verified payment is
        // authoritative, so walk through legal server-side states rather than bypassing the state machine.
        if (status == OnboardingStepStatus.NEEDS_REVISION || status == OnboardingStepStatus.FAILED) {
            commands.transition(invoice.getOrganizationId(), initial.onboardingId(), initial.id(), OnboardingStepStatus.IN_PROGRESS,
                    null, WorkflowActorType.SYSTEM);
            status = OnboardingStepStatus.IN_PROGRESS;
        }
        if (status == OnboardingStepStatus.AVAILABLE && initial.requiresReview()) {
            commands.transition(invoice.getOrganizationId(), initial.onboardingId(), initial.id(), OnboardingStepStatus.IN_PROGRESS,
                    null, WorkflowActorType.SYSTEM);
            status = OnboardingStepStatus.IN_PROGRESS;
        }
        if (initial.requiresReview()) {
            if (status == OnboardingStepStatus.IN_PROGRESS) {
                commands.transition(invoice.getOrganizationId(), initial.onboardingId(), initial.id(), OnboardingStepStatus.SUBMITTED,
                        null, WorkflowActorType.SYSTEM);
                status = OnboardingStepStatus.SUBMITTED;
            }
            if (status == OnboardingStepStatus.SUBMITTED) {
                commands.transition(invoice.getOrganizationId(), initial.onboardingId(), initial.id(), OnboardingStepStatus.UNDER_REVIEW,
                        null, WorkflowActorType.SYSTEM);
                status = OnboardingStepStatus.UNDER_REVIEW;
            }
        }
        if (status == OnboardingStepStatus.AVAILABLE || status == OnboardingStepStatus.IN_PROGRESS
                || status == OnboardingStepStatus.SUBMITTED || status == OnboardingStepStatus.UNDER_REVIEW) {
            commands.transition(invoice.getOrganizationId(), initial.onboardingId(), initial.id(), OnboardingStepStatus.COMPLETED,
                    null, WorkflowActorType.SYSTEM);
        }
    }
}
