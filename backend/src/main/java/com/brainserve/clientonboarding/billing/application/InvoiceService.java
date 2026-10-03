package com.brainserve.clientonboarding.billing.application;

import static com.brainserve.clientonboarding.billing.domain.model.BillingModels.*;
import com.brainserve.clientonboarding.billing.domain.model.InvoicePolicy;
import com.brainserve.clientonboarding.billing.infrastructure.persistence.BillingRepository;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.onboarding.application.StepExecutionService;
import com.brainserve.clientonboarding.project.application.ProjectWorkflowPort;
import com.brainserve.clientonboarding.portal.application.ClientPortalService;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private final BillingRepository repository; private final BillingLedger ledger; private final StepExecutionService steps;
    private final ProjectWorkflowPort projects; private final ClientPortalService portal; private final BillingFingerprint fingerprint; private final Clock clock;
    public InvoiceService(BillingRepository repository,BillingLedger ledger,StepExecutionService steps,ProjectWorkflowPort projects,ClientPortalService portal,BillingFingerprint fingerprint,Clock clock) {this.repository=repository;this.ledger=ledger;this.steps=steps;this.projects=projects;this.portal=portal;this.fingerprint=fingerprint;this.clock=clock;}
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public PageSlice<View> list(TenantPrincipal p,UUID project,String search,Status status,int page,int size) {
        BillingErrors.page(page,size);if(search.length()>100)throw BillingErrors.invalid("Search must be at most 100 characters.");
        var result=repository.page(p.organizationId(),project,search,status,page,size,LocalDate.now(clock));
        return new PageSlice<>(result.items().stream().map(i->view(i,false)).toList(),page,size,result.totalElements());
    }
    public View get(TenantPrincipal p,UUID id,boolean client) {return view(ledger.access(p,id,client),true);}
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public View internalStep(TenantPrincipal p,UUID step) {steps.read(p.organizationId(),step);return repository.byStep(p.organizationId(),step).map(i->view(i,true)).orElse(null);}
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_READ')")
    public View forStep(TenantPrincipal p,UUID project,UUID step) {portal.requireStepAccess(p,project,step);return repository.byStep(p.organizationId(),step).filter(v->v.status()!=Status.DRAFT).map(i->view(i,true)).orElse(null);}
    @PreAuthorize("hasAuthority('INVOICE_CREATE')") @Transactional
    public View create(TenantPrincipal p,Create command,String key) {
        BillingErrors.key(key);String hash=fingerprint.of(command);repository.lockCommands(p.organizationId());
        var duplicate=repository.byKey(p.organizationId(),key,hash);if(duplicate.isPresent())return view(duplicate.get(),true);
        var context=steps.read(p.organizationId(),command.stepId());projects.lockOnboardingProject(p.organizationId(),context.onboarding().projectId());context=steps.read(p.organizationId(),command.stepId());
        if(context.step().stepType()!=TemplateStep.StepType.PAYMENT || !context.step().applicable())throw BillingErrors.invalid("Select an applicable payment step.");
        if(repository.byStep(p.organizationId(),command.stepId()).isPresent())throw BillingErrors.state("This step already has an invoice.");
        if(command.dueDate()==null || command.dueDate().isBefore(LocalDate.now(clock)) || command.dueDate().isAfter(LocalDate.now(clock).plusYears(10)))throw BillingErrors.invalid("Choose a due date from today to ten years from today.");
        if(command.note()!=null && command.note().length()>2000)throw BillingErrors.invalid("Note must be at most 2000 characters.");
        Totals totals;long threshold;Policy policy;
        try {totals=InvoicePolicy.calculate(command.items());threshold=InvoicePolicy.threshold(context.step().configuration(),totals.totalMinor());policy=InvoicePolicy.policy(context.step().configuration());}
        catch(IllegalArgumentException|ArithmeticException e){throw BillingErrors.invalid(e.getMessage());}
        UUID id=UUID.randomUUID();var i=new Invoice(id,p.organizationId(),context.onboarding().projectId(),command.stepId(),"INV-"+id.toString().toUpperCase(Locale.ROOT),"INR",policy,totals.subtotalMinor(),totals.taxMinor(),totals.totalMinor(),threshold,0,0,0,Status.DRAFT,command.dueDate(),command.note(),null,null,clock.instant(),0);
        repository.insert(i,totals.items(),key,hash,p.userId());ledger.event(i,"INVOICE_CREATED",p.userId(),"API");return view(i,true);
    }
    @PreAuthorize("hasAuthority('INVOICE_SEND')") @Transactional
    public View publish(TenantPrincipal p,UUID id,long version) {
        var i=ledger.lock(p.organizationId(),id);if(i.version()!=version)throw BillingErrors.conflict();
        if(i.status()!=Status.DRAFT)throw BillingErrors.state("Only draft invoices can be published.");
        projects.lockOnboardingProject(p.organizationId(),i.projectId());
        if(!repository.update(i,Status.SENT,0,0,0,p.userId(),clock.instant()))throw BillingErrors.conflict();
        i=ledger.require(p.organizationId(),id);ledger.sync(i,p.userId());ledger.event(i,"INVOICE_SENT",p.userId(),"API");return view(i,true);
    }
    @PreAuthorize("hasAuthority('INVOICE_SEND')") @Transactional
    public View close(TenantPrincipal p,UUID id,long version,Status status,String reason) {
        BillingErrors.reason(reason);var i=ledger.lock(p.organizationId(),id);
        if(i.version()!=version)throw BillingErrors.conflict();
        if((status!=Status.VOID && status!=Status.CANCELLED) || i.closed() || i.capturedMinor()!=0 || i.reservedMinor()!=0)throw BillingErrors.state("Only unpaid invoices without pending payments can be voided or cancelled.");
        if(!repository.update(i,status,0,0,0,p.userId(),clock.instant()))throw BillingErrors.conflict();
        repository.closeReason(i,reason.trim());i=ledger.require(p.organizationId(),id);ledger.sync(i,p.userId());ledger.event(i,"INVOICE_"+status,p.userId(),"API");return view(i,true);
    }
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_READ')") @Transactional
    public View viewed(TenantPrincipal p,UUID id) {ledger.access(p,id,true);var i=ledger.lock(p.organizationId(),id);if(i.status()==Status.SENT){repository.update(i,Status.VIEWED,i.capturedMinor(),i.refundedMinor(),i.reservedMinor(),p.userId(),clock.instant());i=ledger.require(p.organizationId(),id);}return view(i,true);}
    private View view(Invoice i,boolean detail) {
        Status display=i.status()!=Status.DRAFT && !i.closed() && i.balanceMinor()>0 && i.dueDate().isBefore(LocalDate.now(clock))?Status.OVERDUE:i.status();
        return new View(i,detail?repository.items(i.organizationId(),i.id()):List.of(),display,i.paidMinor(),i.balanceMinor(),Math.max(0,i.thresholdMinor()-i.paidMinor()),i.satisfied());
    }
    public record Create(@jakarta.validation.constraints.NotNull UUID stepId,@jakarta.validation.constraints.NotNull LocalDate dueDate,@jakarta.validation.constraints.Size(max=2000) String note,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=1,max=50) List<ItemInput> items){}
    public record View(Invoice invoice,List<Item> items,Status displayStatus,long paidMinor,long balanceMinor,long thresholdRemainingMinor,boolean requirementSatisfied){}
}
