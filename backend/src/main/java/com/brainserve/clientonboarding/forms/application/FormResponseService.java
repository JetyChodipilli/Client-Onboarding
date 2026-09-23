package com.brainserve.clientonboarding.forms.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.forms.domain.model.*;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.*;
import com.brainserve.clientonboarding.forms.domain.repository.FormRepository;
import com.brainserve.clientonboarding.onboarding.application.StepExecutionService;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance.Status;
import com.brainserve.clientonboarding.portal.application.ClientPortalService;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormResponseService {
    private final FormRepository repository;
    private final FormStepConfiguration configurations;
    private final StepExecutionService steps;
    private final ClientPortalService portal;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;
    public FormResponseService(FormRepository repository,FormStepConfiguration configurations,StepExecutionService steps,
                               ClientPortalService portal,AuditService audit,ObjectMapper json,Clock clock) {
        this.repository=repository;this.configurations=configurations;this.steps=steps;this.portal=portal;this.audit=audit;this.json=json;this.clock=clock;
    }
    @PreAuthorize("hasAnyAuthority('FORM_READ','FORM_REVIEW')")
    public View internal(TenantPrincipal p,UUID stepId) { return view(p,steps.read(p.organizationId(),stepId)); }
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_READ')")
    public View client(TenantPrincipal p,UUID projectId,UUID stepId) {
        portal.requireStepAccess(p,projectId,stepId);
        return view(p,steps.read(p.organizationId(),stepId));
    }
    @PreAuthorize("hasAnyAuthority('FORM_READ','FORM_REVIEW','CLIENT_PORTAL_READ')")
    public PageSlice<Submission> history(TenantPrincipal p,UUID projectId,UUID stepId,int page,int size) {
        FormErrors.page(page,size,20);
        if(p.hasPermission("CLIENT_PORTAL_READ")) portal.requireStepAccess(p,projectId,stepId);
        view(p,steps.read(p.organizationId(),stepId));
        return repository.response(p.organizationId(),stepId).map(r -> repository.submissions(p.organizationId(),r.id(),page,size))
                .orElseGet(() -> new PageSlice<>(List.of(),page,size,0));
    }
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')")
    @Transactional
    public View save(TenantPrincipal p,UUID projectId,UUID stepId,long version,Map<String,Object> answers,boolean submit,RequestMetadata metadata) {
        portal.requireStepAccess(p,projectId,stepId);
        var context=steps.lockActive(p.organizationId(),stepId);
        // Recheck grants after taking the project lock, before persisting anything.
        portal.requireStepAccess(p,projectId,stepId);
        View current=view(p,context);
        if(!Set.of(Status.AVAILABLE,Status.IN_PROGRESS,Status.NEEDS_REVISION).contains(context.step().status())
                || !Set.of(ResponseStatus.DRAFT,ResponseStatus.NEEDS_REVISION).contains(current.response().status()))
            throw FormErrors.state("FORM_NOT_EDITABLE","This form is locked or already submitted. Wait for review or a revision request.");
        if(version!=current.response().version()) throw FormErrors.conflict();
        Map<String,Object> clean;
        try { clean=FormPolicy.answers(current.definition().fields(),answers,submit); }
        catch(FormPolicy.InvalidAnswer e) { throw FormErrors.invalid(e); }
        try { if(json.writeValueAsString(answers).length()>100000) throw FormErrors.invalid("Answers exceed 100000 characters."); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
        var old=current.response(); var now=clock.instant();
        ResponseStatus target=submit?(context.step().requiresReview()?ResponseStatus.SUBMITTED:ResponseStatus.APPROVED):ResponseStatus.DRAFT;
        var response=new Response(old.id()==null?UUID.randomUUID():old.id(),p.organizationId(),stepId,old.formVersionId(),target,
                clean,old.submissionNumber()+(submit?1:0),old.reviewNote(),now,version+1);
        persist(response,version,p.userId());
        if(submit) repository.insertSubmission(p.organizationId(),response,p.userId(),now);
        steps.transition(context,submit?(context.step().requiresReview()?Status.SUBMITTED:Status.COMPLETED):Status.IN_PROGRESS,p.userId(),now);
        log(p,submit?"FORM_SUBMITTED":"FORM_DRAFT_SAVED",response,metadata);
        return view(p,steps.read(p.organizationId(),stepId));
    }
    @PreAuthorize("hasAuthority('FORM_REVIEW')")
    @Transactional
    public View review(TenantPrincipal p,UUID stepId,long version,ReviewDecision decision,String note,RequestMetadata metadata) {
        var context=steps.lockActive(p.organizationId(),stepId);
        View current=view(p,context); var old=current.response();
        if(old.version()!=version) throw FormErrors.conflict();
        if(!Set.of(ResponseStatus.SUBMITTED,ResponseStatus.UNDER_REVIEW).contains(old.status())
                || !Set.of(Status.SUBMITTED,Status.UNDER_REVIEW).contains(context.step().status())
                || decision==ReviewDecision.UNDER_REVIEW && old.status()!=ResponseStatus.SUBMITTED)
            throw FormErrors.state("INVALID_FORM_REVIEW","Only a current submitted response can be reviewed.");
        String clean=note==null?null:note.trim();
        if(decision==ReviewDecision.NEEDS_REVISION && (clean==null||clean.isBlank())) throw FormErrors.invalid("Explain what the client needs to revise.");
        var response=new Response(old.id(),p.organizationId(),stepId,old.formVersionId(),ResponseStatus.valueOf(decision.name()),old.answers(),old.submissionNumber(),clean,clock.instant(),version+1);
        persist(response,version,p.userId());
        repository.insertReview(p.organizationId(),response,decision,clean,p.userId(),clock.instant());
        steps.transition(context,decision==ReviewDecision.APPROVED?Status.COMPLETED:Status.valueOf(decision.name()),p.userId(),clock.instant());
        log(p,"FORM_"+decision.name(),response,metadata);
        return view(p,steps.read(p.organizationId(),stepId));
    }
    private View view(TenantPrincipal p,StepExecutionService.Context context) {
        if(context.step().stepType()!=TemplateStep.StepType.FORM||!context.step().applicable()) throw FormErrors.missing();
        var definition=configurations.require(p.organizationId(),context.step().configuration());
        var response=repository.response(p.organizationId(),context.step().id()).orElseGet(() -> new Response(null,p.organizationId(),context.step().id(),definition.id(),ResponseStatus.DRAFT,Map.of(),0,null,null,0));
        if(!response.formVersionId().equals(definition.id())) throw new IllegalStateException("Form binding changed after snapshot");
        var template=repository.template(p.organizationId(),definition.formId()).orElseThrow(FormErrors::missing);
        return new View(template.name(),context.onboarding().projectId(),context.step().name(),context.step().status().name(),
                context.projectStatus(),context.onboarding().status().name(),context.step().dueAt(),definition,response);
    }
    private void persist(Response r,long version,UUID actor) {
        if(version==0) repository.insertResponse(r,actor);
        else if(!repository.updateResponse(r,version,actor)) throw FormErrors.conflict();
    }
    private void log(TenantPrincipal p,String action,Response r,RequestMetadata m) {
        audit.append(p.organizationId(),p.userId(),action,"FORM_RESPONSE",r.id(),Map.of(),Map.of("status",r.status(),"submissionNumber",r.submissionNumber(),"stepId",r.stepId()),"API",m.ipHash());
        if(!action.equals("FORM_DRAFT_SAVED")) repository.event(r,action,
                com.brainserve.clientonboarding.common.observability.RequestIds.currentCorrelationId(),clock.instant());
    }
    public record View(String formName,UUID projectId,String stepName,String stepStatus,String projectStatus,
                       String onboardingStatus,java.time.Instant deadline,Definition definition,Response response) { }
}
