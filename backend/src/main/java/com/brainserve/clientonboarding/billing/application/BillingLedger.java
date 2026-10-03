package com.brainserve.clientonboarding.billing.application;

import static com.brainserve.clientonboarding.billing.domain.model.BillingModels.*;
import com.brainserve.clientonboarding.billing.domain.model.InvoicePolicy;
import com.brainserve.clientonboarding.billing.infrastructure.persistence.BillingRepository;
import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.onboarding.application.StepExecutionService;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.portal.application.ClientPortalService;
import com.brainserve.clientonboarding.project.application.ProjectWorkflowPort;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Payments calls this boundary inside its transaction; only billing mutates invoice balances. */
@Service
public class BillingLedger {
    private final BillingRepository repository;
    private final ProjectWorkflowPort projects;
    private final StepExecutionService steps;
    private final ClientPortalService portal;
    private final AuditService audit;
    private final Clock clock;
    public BillingLedger(BillingRepository repository,ProjectWorkflowPort projects,StepExecutionService steps,ClientPortalService portal,AuditService audit,Clock clock) {this.repository=repository;this.projects=projects;this.steps=steps;this.portal=portal;this.audit=audit;this.clock=clock;}
    public Invoice require(UUID org,UUID id) {return repository.find(org,id).orElseThrow(BillingErrors::missing);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockCommands(UUID org) {repository.lockCommands(org);}
    public Invoice access(TenantPrincipal p,UUID id,boolean client) {
        if(client) {
            if(!p.hasPermission("CLIENT_PORTAL_READ"))throw new AccessDeniedException("Client access required");
            var i=require(p.organizationId(),id);portal.requireStepAccess(p,i.projectId(),i.stepId());
            if(i.status()==Status.DRAFT)throw BillingErrors.missing();return i;
        }
        if(!p.hasPermission("INVOICE_READ"))throw new AccessDeniedException("Invoice read permission required");
        return require(p.organizationId(),id);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Invoice lock(UUID org,UUID id) {
        var initial=require(org,id);projects.lockProject(org,initial.projectId());
        return repository.lock(org,id).orElseThrow(BillingErrors::missing);
    }
    public void accepting(Invoice i) {
        var c=steps.read(i.organizationId(),i.stepId());
        if(i.status()==Status.DRAFT || i.closed() || !c.projectStatus().equals("ONBOARDING") || c.onboarding().status()!=OnboardingInstance.Status.IN_PROGRESS
           || !Set.of(OnboardingStepInstance.Status.AVAILABLE,OnboardingStepInstance.Status.IN_PROGRESS,OnboardingStepInstance.Status.NEEDS_REVISION,OnboardingStepInstance.Status.COMPLETED).contains(c.step().status()))
            throw BillingErrors.state("This invoice is not accepting payments. Contact your project team.");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Invoice balances(Invoice current,long captured,long refunded,long reserved,UUID actor,String event,String source) {
        if(captured<0 || refunded<0 || refunded>captured || reserved<0 || captured-refunded+reserved>current.totalMinor())throw BillingErrors.state("Payment evidence exceeds the available invoice balance.");
        if(!repository.update(current,InvoicePolicy.status(current,captured,refunded),captured,refunded,reserved,actor,clock.instant()))throw BillingErrors.conflict();
        var updated=require(current.organizationId(),current.id());
        sync(updated,actor);
        event(updated,event,actor,source);
        return updated;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void sync(Invoice i,UUID actor) {
        var c=steps.read(i.organizationId(),i.stepId());
        boolean complete=c.step().status()==OnboardingStepInstance.Status.COMPLETED;
        // Refunds invalidate readiness even while paused. Positive completion waits for an active instance.
        if(complete && !i.satisfied())steps.transition(c,OnboardingStepInstance.Status.NEEDS_REVISION,actor,clock.instant());
        else if(!complete && i.satisfied() && c.projectStatus().equals("ONBOARDING") && c.onboarding().status()==OnboardingInstance.Status.IN_PROGRESS
                && Set.of(OnboardingStepInstance.Status.AVAILABLE,OnboardingStepInstance.Status.IN_PROGRESS,OnboardingStepInstance.Status.NEEDS_REVISION).contains(c.step().status()))
            steps.transition(c,OnboardingStepInstance.Status.COMPLETED,actor,clock.instant());
    }
    public void event(Invoice i,String event,UUID actor,String source) {
        repository.event(i,event,RequestIds.currentRequestId(),clock.instant());
        audit.append(i.organizationId(),actor,event,"INVOICE",i.id(),Map.of(),Map.of("status",i.status(),"paidMinor",i.paidMinor(),"reservedMinor",i.reservedMinor()),source,null);
    }
}
