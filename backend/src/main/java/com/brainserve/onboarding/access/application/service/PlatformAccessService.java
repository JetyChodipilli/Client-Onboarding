package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.access.api.request.PlatformAccessReviewRequest;
import com.brainserve.onboarding.access.api.request.SubmitPlatformAccessRequest;
import com.brainserve.onboarding.access.api.response.*;
import com.brainserve.onboarding.access.domain.model.*;
import com.brainserve.onboarding.access.infrastructure.persistence.*;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.application.service.*;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAccessService {
    private static final int MAX_PAGE_SIZE=100;
    private final PlatformAccessRequestRepository requests;
    private final PlatformAccessReviewRepository reviews;
    private final ClientPortalAccessService clientAccess;
    private final OnboardingStepAccessService stepAccess;
    private final OnboardingStepCommandService stepCommands;
    private final PlatformAccessSafetyPolicy safety;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public PlatformAccessService(PlatformAccessRequestRepository requests,PlatformAccessReviewRepository reviews,
                                 ClientPortalAccessService clientAccess,OnboardingStepAccessService stepAccess,
                                 OnboardingStepCommandService stepCommands,PlatformAccessSafetyPolicy safety,
                                 ActivityTimelineService activity,AuditService audit,OutboxService outbox,Clock clock){
        this.requests=requests;this.reviews=reviews;this.clientAccess=clientAccess;this.stepAccess=stepAccess;this.stepCommands=stepCommands;
        this.safety=safety;this.activity=activity;this.audit=audit;this.outbox=outbox;this.clock=clock;
    }

    @Transactional(readOnly=true)
    public PageResult<PlatformAccessQueueItemResponse> list(TenantPrincipal principal,int page,int size,UUID projectId,PlatformAccessRequestStatus status){
        Pageable pageable=PageRequest.of(Math.max(0,page),safeSize(size),Sort.by(Sort.Direction.DESC,"updatedAt").and(Sort.by(Sort.Direction.DESC,"id")));
        Page<PlatformAccessRequest> result;
        if(projectId!=null&&status!=null) result=requests.findAllByOrganizationIdAndProjectIdAndStatus(principal.organizationId(),projectId,status,pageable);
        else if(projectId!=null) result=requests.findAllByOrganizationIdAndProjectId(principal.organizationId(),projectId,pageable);
        else if(status!=null) result=requests.findAllByOrganizationIdAndStatus(principal.organizationId(),status,pageable);
        else result=requests.findAllByOrganizationId(principal.organizationId(),pageable);
        return new PageResult<>(result.getContent().stream().map(PlatformAccessService::queue).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public PlatformAccessRequestResponse get(TenantPrincipal principal,UUID requestId){return response(require(principal.organizationId(),requestId),true);}

    @Transactional(readOnly=true)
    public PlatformAccessRequestResponse clientGet(ClientPrincipal principal,UUID projectId,UUID stepId){
        clientAccess.requireProjectActionAccess(principal.organizationId(),principal.userId(),projectId);
        var step=stepAccess.requireForProject(principal.organizationId(),projectId,stepId); requireClientStep(step);
        PlatformAccessRequest request=requests.findByOrganizationIdAndProjectIdAndStepInstanceId(principal.organizationId(),projectId,stepId).orElseThrow(PlatformAccessService::notFound);
        return response(request,false);
    }

    @Transactional
    public PlatformAccessRequestResponse clientSubmit(ClientPrincipal principal,UUID projectId,UUID stepId,SubmitPlatformAccessRequest body,HttpServletRequest servletRequest){
        clientAccess.requireProjectActionAccess(principal.organizationId(),principal.userId(),projectId);
        var readStep=stepAccess.requireForProject(principal.organizationId(),projectId,stepId); requireClientStep(readStep);
        stepCommands.lockStepContext(principal.organizationId(),readStep.onboardingId(),stepId);
        PlatformAccessRequest request=requests.findByStepForUpdate(principal.organizationId(),stepId).orElseThrow(PlatformAccessService::notFound);
        if(!request.getProjectId().equals(projectId)) throw notFound(); requireVersion(request.getVersion(),body.version());
        safety.validateClientSubmission(body.accountIdentifier(),body.note());
        PlatformAccessRequestStatus before=request.getStatus(); Instant now=clock.instant();
        try{request.submit(body.accountIdentifier(),body.note(),principal.userId(),now);}catch(IllegalArgumentException|IllegalStateException ex){throw state(ex.getMessage());}
        advanceClientSubmission(principal.organizationId(),request.getOnboardingId(),stepId,principal.userId());
        requests.saveAndFlush(request);
        activity.recordClient(principal.organizationId(),request.getClientId(),projectId,principal.userId(),"ACCESS_SUBMITTED","PLATFORM_ACCESS_REQUEST",request.getId(),"Platform access submitted for verification",Map.of("accessType",request.getAccessTypeNameSnapshot()));
        audit.recordClient(principal.organizationId(),principal.userId(),"ACCESS_SUBMITTED","PLATFORM_ACCESS_REQUEST",request.getId(),Map.of("status",before.name()),Map.of("status",request.getStatus().name()),servletRequest);
        outbox.record(principal.organizationId(),"ACCESS_SUBMITTED","PLATFORM_ACCESS_REQUEST",request.getId(),Map.of("requestId",request.getId(),"projectId",projectId,"stepId",stepId));
        return response(request,false);
    }

    @Transactional
    public PlatformAccessRequestResponse startVerification(TenantPrincipal principal,UUID requestId,PlatformAccessReviewRequest body,HttpServletRequest servletRequest){
        PlatformAccessRequest seed=require(principal.organizationId(),requestId); stepCommands.lockStepContext(principal.organizationId(),seed.getOnboardingId(),seed.getStepInstanceId());
        PlatformAccessRequest request=requireForUpdate(principal.organizationId(),requestId); requireVersion(request.getVersion(),body.version()); PlatformAccessRequestStatus before=request.getStatus(); Instant now=clock.instant();
        try{request.startVerification(principal.userId(),now);}catch(IllegalStateException ex){throw state(ex.getMessage());}
        reviews.saveAndFlush(new PlatformAccessReview(UUID.randomUUID(),principal.organizationId(),requestId,request.getProjectId(),PlatformAccessReviewAction.START_VERIFICATION,body.reason(),principal.userId(),now)); requests.saveAndFlush(request);
        recordInternal(principal,request,"ACCESS_VERIFICATION_STARTED",before,servletRequest,body.reason()); return response(request,true);
    }

    @Transactional
    public PlatformAccessRequestResponse verify(TenantPrincipal principal,UUID requestId,PlatformAccessReviewRequest body,HttpServletRequest servletRequest){
        PlatformAccessRequest seed=require(principal.organizationId(),requestId); var locked=stepCommands.lockStepContext(principal.organizationId(),seed.getOnboardingId(),seed.getStepInstanceId());
        PlatformAccessRequest request=requireForUpdate(principal.organizationId(),requestId); requireVersion(request.getVersion(),body.version());
        if(locked.stepType()!=WorkflowStepType.PLATFORM_ACCESS||locked.status()!=OnboardingStepStatus.UNDER_REVIEW) throw state("The workflow step is not awaiting internal verification.");
        PlatformAccessRequestStatus before=request.getStatus(); Instant now=clock.instant();
        try{request.verify(principal.userId(),now);}catch(IllegalStateException ex){throw state(ex.getMessage());}
        reviews.saveAndFlush(new PlatformAccessReview(UUID.randomUUID(),principal.organizationId(),requestId,request.getProjectId(),PlatformAccessReviewAction.VERIFY,body.reason(),principal.userId(),now)); requests.saveAndFlush(request);
        stepCommands.transition(principal.organizationId(),request.getOnboardingId(),request.getStepInstanceId(),OnboardingStepStatus.COMPLETED,principal.userId(),WorkflowActorType.INTERNAL);
        recordInternal(principal,request,"ACCESS_VERIFIED",before,servletRequest,body.reason()); outbox.record(principal.organizationId(),"ACCESS_VERIFIED","PLATFORM_ACCESS_REQUEST",requestId,Map.of("requestId",requestId,"projectId",request.getProjectId(),"stepId",request.getStepInstanceId())); return response(request,true);
    }

    @Transactional
    public PlatformAccessRequestResponse requestRevision(TenantPrincipal principal,UUID requestId,PlatformAccessReviewRequest body,HttpServletRequest servletRequest){
        if(body.reason()==null||body.reason().isBlank()) throw invalid("A revision reason is required.");
        PlatformAccessRequest seed=require(principal.organizationId(),requestId); var locked=stepCommands.lockStepContext(principal.organizationId(),seed.getOnboardingId(),seed.getStepInstanceId());
        PlatformAccessRequest request=requireForUpdate(principal.organizationId(),requestId); requireVersion(request.getVersion(),body.version());
        if(locked.status()!=OnboardingStepStatus.UNDER_REVIEW) throw state("The workflow step is not under review."); PlatformAccessRequestStatus before=request.getStatus(); Instant now=clock.instant();
        try{request.requestRevision(principal.userId(),now);}catch(IllegalStateException ex){throw state(ex.getMessage());}
        reviews.saveAndFlush(new PlatformAccessReview(UUID.randomUUID(),principal.organizationId(),requestId,request.getProjectId(),PlatformAccessReviewAction.REQUEST_REVISION,body.reason(),principal.userId(),now)); requests.saveAndFlush(request);
        stepCommands.transition(principal.organizationId(),request.getOnboardingId(),request.getStepInstanceId(),OnboardingStepStatus.NEEDS_REVISION,principal.userId(),WorkflowActorType.INTERNAL);
        recordInternal(principal,request,"ACCESS_REVISION_REQUESTED",before,servletRequest,body.reason()); outbox.record(principal.organizationId(),"ACCESS_REVISION_REQUESTED","PLATFORM_ACCESS_REQUEST",requestId,Map.of("requestId",requestId,"projectId",request.getProjectId(),"stepId",request.getStepInstanceId())); return response(request,true);
    }

    @Transactional
    public PlatformAccessRequestResponse waive(TenantPrincipal principal,UUID requestId,PlatformAccessReviewRequest body,HttpServletRequest servletRequest){
        if(body.reason()==null||body.reason().isBlank()) throw invalid("A waiver reason is required.");
        PlatformAccessRequest seed=require(principal.organizationId(),requestId); var locked=stepCommands.lockStepContext(principal.organizationId(),seed.getOnboardingId(),seed.getStepInstanceId());
        PlatformAccessRequest request=requireForUpdate(principal.organizationId(),requestId); requireVersion(request.getVersion(),body.version());
        if(!locked.allowSkip()) throw new ApiException(HttpStatus.CONFLICT,"ACCESS_WAIVER_NOT_ALLOWED","This workflow requirement does not allow waiver.");
        if(locked.status()==OnboardingStepStatus.LOCKED||locked.status()==OnboardingStepStatus.UNDER_REVIEW||locked.status()==OnboardingStepStatus.SUBMITTED||locked.status()==OnboardingStepStatus.COMPLETED) throw state("The workflow step cannot be waived in its current state.");
        PlatformAccessRequestStatus before=request.getStatus(); Instant now=clock.instant();
        try{request.waive(principal.userId(),now);}catch(IllegalStateException ex){throw state(ex.getMessage());}
        reviews.saveAndFlush(new PlatformAccessReview(UUID.randomUUID(),principal.organizationId(),requestId,request.getProjectId(),PlatformAccessReviewAction.WAIVE,body.reason(),principal.userId(),now)); requests.saveAndFlush(request);
        if(locked.status()==OnboardingStepStatus.NEEDS_REVISION) stepCommands.transition(principal.organizationId(),request.getOnboardingId(),request.getStepInstanceId(),OnboardingStepStatus.IN_PROGRESS,principal.userId(),WorkflowActorType.INTERNAL);
        stepCommands.transition(principal.organizationId(),request.getOnboardingId(),request.getStepInstanceId(),OnboardingStepStatus.SKIPPED,principal.userId(),WorkflowActorType.INTERNAL);
        recordInternal(principal,request,"ACCESS_WAIVED",before,servletRequest,body.reason()); outbox.record(principal.organizationId(),"ACCESS_WAIVED","PLATFORM_ACCESS_REQUEST",requestId,Map.of("requestId",requestId,"projectId",request.getProjectId(),"stepId",request.getStepInstanceId(),"reason",body.reason().trim())); return response(request,true);
    }

    private void advanceClientSubmission(UUID org,UUID onboardingId,UUID stepId,UUID actor){
        var state=stepCommands.lockStepContext(org,onboardingId,stepId).status();
        if(state==OnboardingStepStatus.NEEDS_REVISION||state==OnboardingStepStatus.AVAILABLE) { stepCommands.transition(org,onboardingId,stepId,OnboardingStepStatus.IN_PROGRESS,actor,WorkflowActorType.CLIENT); state=OnboardingStepStatus.IN_PROGRESS; }
        if(state==OnboardingStepStatus.IN_PROGRESS) { stepCommands.transition(org,onboardingId,stepId,OnboardingStepStatus.SUBMITTED,actor,WorkflowActorType.CLIENT); state=OnboardingStepStatus.SUBMITTED; }
        if(state==OnboardingStepStatus.SUBMITTED) { stepCommands.transition(org,onboardingId,stepId,OnboardingStepStatus.UNDER_REVIEW,actor,WorkflowActorType.CLIENT); state=OnboardingStepStatus.UNDER_REVIEW; }
        if(state!=OnboardingStepStatus.UNDER_REVIEW) throw state("The workflow step cannot accept a platform access submission in its current state.");
    }

    private void recordInternal(TenantPrincipal principal,PlatformAccessRequest request,String action,PlatformAccessRequestStatus before,HttpServletRequest servletRequest,String reason){
        Map<String,Object> metadata=new LinkedHashMap<>(); metadata.put("accessType",request.getAccessTypeNameSnapshot()); if(reason!=null&&!reason.isBlank())metadata.put("reason",reason.trim());
        activity.record(principal.organizationId(),request.getClientId(),request.getProjectId(),principal.userId(),action,"PLATFORM_ACCESS_REQUEST",request.getId(),action.replace('_',' ').toLowerCase(Locale.ROOT),metadata);
        audit.record(principal.organizationId(),principal.userId(),action,"PLATFORM_ACCESS_REQUEST",request.getId(),Map.of("status",before.name()),Map.of("status",request.getStatus().name()),servletRequest);
    }

    private PlatformAccessRequestResponse response(PlatformAccessRequest request,boolean includeReviews){
        var step=stepAccess.requireForProject(request.getOrganizationId(),request.getProjectId(),request.getStepInstanceId());
        List<PlatformAccessReviewResponse> history=includeReviews?reviews.findAllByOrganizationIdAndAccessRequestIdOrderByCreatedAtAscIdAsc(request.getOrganizationId(),request.getId()).stream().map(r->new PlatformAccessReviewResponse(r.getId(),r.getAction(),r.getReason(),r.getCreatedAt(),r.getCreatedBy())).toList():List.of();
        return new PlatformAccessRequestResponse(request.getId(),request.getProjectId(),request.getOnboardingId(),request.getStepInstanceId(),step.name(),request.getAccessTypeCodeSnapshot(),request.getAccessTypeNameSnapshot(),request.getGuideDescriptionSnapshot(),request.getGuideVersionNumber(),request.getInstructionsSnapshot(),request.getHelpUrlSnapshot(),request.getResourcesSnapshot(),request.getStatus(),request.getClientAccountIdentifier(),request.getClientSubmissionNote(),request.getSubmittedAt(),request.getVerificationStartedAt(),request.getVerifiedAt(),request.getRevisionRequestedAt(),request.getWaivedAt(),request.getVersion(),history);
    }
    private static PlatformAccessQueueItemResponse queue(PlatformAccessRequest r){return new PlatformAccessQueueItemResponse(r.getId(),r.getProjectId(),r.getOnboardingId(),r.getStepInstanceId(),r.getAccessTypeCodeSnapshot(),r.getAccessTypeNameSnapshot(),r.getStatus(),r.getClientAccountIdentifier(),r.getSubmittedAt(),r.getUpdatedAt(),r.getVersion());}
    private static void requireClientStep(OnboardingStepAccessService.StepRef step){if(step.stepType()!=WorkflowStepType.PLATFORM_ACCESS||!step.clientVisible())throw notFound();}
    private PlatformAccessRequest require(UUID org,UUID id){return requests.findByOrganizationIdAndId(org,id).orElseThrow(PlatformAccessService::notFound);} private PlatformAccessRequest requireForUpdate(UUID org,UUID id){return requests.findForUpdate(org,id).orElseThrow(PlatformAccessService::notFound);}
    private static void requireVersion(long actual,long expected){if(actual!=expected)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","This access request changed. Refresh and try again.");}
    private static int safeSize(int size){return Math.min(Math.max(size,1),MAX_PAGE_SIZE);} private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested platform access resource was not found.");} private static ApiException state(String message){return new ApiException(HttpStatus.CONFLICT,"PLATFORM_ACCESS_STATE_INVALID",message);} private static ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"PLATFORM_ACCESS_INVALID",message);}
    public record PageResult<T>(List<T> items,int page,int size,long totalElements,int totalPages){public PageResult{items=List.copyOf(items);}}
}
