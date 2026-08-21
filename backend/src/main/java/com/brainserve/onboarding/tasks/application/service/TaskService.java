package com.brainserve.onboarding.tasks.application.service;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.*;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.notifications.application.service.NotificationService;
import com.brainserve.onboarding.notifications.domain.model.NotificationChannel;
import com.brainserve.onboarding.onboarding.application.service.WorkflowActorType;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.project.application.service.ProjectWorkflowAccessService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.tasks.api.request.*;
import com.brainserve.onboarding.tasks.api.response.TaskResponse;
import com.brainserve.onboarding.tasks.domain.model.*;
import com.brainserve.onboarding.tasks.infrastructure.persistence.TaskRepository;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TaskService {
    private static final int MAX_PAGE_SIZE=100;
    private final TaskRepository tasks; private final ProjectWorkflowAccessService projects; private final OnboardingStepCommandService workflow;
    private final ActivityTimelineService activity; private final AuditService audit; private final OutboxService outbox; private final NotificationService notificationService; private final ClientPortalAccessService clientAccess; private final TaskAssignmentPolicy assignmentPolicy; private final Clock clock;
    public TaskService(TaskRepository tasks,ProjectWorkflowAccessService projects,OnboardingStepCommandService workflow,ActivityTimelineService activity,AuditService audit,OutboxService outbox,NotificationService notificationService,ClientPortalAccessService clientAccess,TaskAssignmentPolicy assignmentPolicy,Clock clock){this.tasks=tasks;this.projects=projects;this.workflow=workflow;this.activity=activity;this.audit=audit;this.outbox=outbox;this.notificationService=notificationService;this.clientAccess=clientAccess;this.assignmentPolicy=assignmentPolicy;this.clock=clock;}
    @Transactional public TaskResponse create(TenantPrincipal p,CreateTaskRequest r,HttpServletRequest req){
        validateScope(p.organizationId(),r.projectId(),r.onboardingId(),r.stepId()); assignmentPolicy.validate(p.organizationId(),r.projectId(),r.assignedUserId(),r.assignedUserType(),r.assignedRoleId()); Instant now=clock.instant();
        TaskItem t=tasks.saveAndFlush(new TaskItem(UUID.randomUUID(),p.organizationId(),r.projectId(),r.onboardingId(),r.stepId(),r.title(),r.description(),TaskType.MANUAL,r.priority(),r.assignedUserId(),r.assignedUserType(),r.assignedRoleId(),r.dueAt(),null,p.userId(),"INTERNAL",now));
        record(p,t,"TASK_CREATED",req); return map(t);
    }
    @Transactional(readOnly=true) public PageResult list(TenantPrincipal p,int page,int size,UUID projectId,TaskStatus status,boolean mine){
        Pageable pageable=PageRequest.of(Math.max(0,page),safeSize(size),Sort.by(Sort.Direction.ASC,"dueAt").and(Sort.by(Sort.Direction.DESC,"createdAt")));
        Page<TaskItem> result=mine?tasks.findAllByOrganizationIdAndAssignedUserIdAndAssignedUserType(p.organizationId(),p.userId(),"INTERNAL",pageable):projectId!=null?tasks.findAllByOrganizationIdAndProjectId(p.organizationId(),projectId,pageable):status!=null?tasks.findAllByOrganizationIdAndStatus(p.organizationId(),status,pageable):tasks.findAllByOrganizationId(p.organizationId(),pageable);
        return new PageResult(result.getContent().stream().map(TaskService::map).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }
    @Transactional(readOnly=true) public TaskResponse get(TenantPrincipal p,UUID id){return map(tasks.findByOrganizationIdAndId(p.organizationId(),id).orElseThrow(TaskService::notFound));}
    @Transactional public TaskResponse update(TenantPrincipal p,UUID id,UpdateTaskRequest r,HttpServletRequest req){TaskItem t=locked(p.organizationId(),id,r.version());assignmentPolicy.validate(p.organizationId(),t.getProjectId(),r.assignedUserId(),r.assignedUserType(),r.assignedRoleId());try{t.edit(r.title(),r.description(),r.priority(),r.assignedUserId(),r.assignedUserType(),r.assignedRoleId(),r.dueAt(),p.userId(),"INTERNAL",clock.instant());}catch(IllegalArgumentException|IllegalStateException ex){throw conflict(ex.getMessage());}tasks.saveAndFlush(t);record(p,t,"TASK_UPDATED",req);return map(t);}
    @Transactional
    public TaskResponse transition(TenantPrincipal p,UUID id,TransitionTaskRequest r,HttpServletRequest req){
        LockedTask locked=lockedWithWorkflowContext(p.organizationId(),id,r.version());
        TaskItem t=locked.task();
        if(r.status()==TaskStatus.COMPLETED&&locked.workflowContext()!=null&&locked.workflowContext().requiresReview()&&t.getStatus()!=TaskStatus.IN_REVIEW){
            throw new ApiException(HttpStatus.CONFLICT,"TASK_REVIEW_REQUIRED","Workflow tasks that require review must enter IN_REVIEW before they can be completed.");
        }
        transitionInternal(t,r.status(),p.userId(),"INTERNAL");
        tasks.saveAndFlush(t);
        syncWorkflow(t,p.userId(),WorkflowActorType.INTERNAL);
        record(p,t,"TASK_"+t.getStatus().name(),req);
        return map(t);
    }
    @Transactional(readOnly=true) public PageResult clientMine(ClientPrincipal p,int page,int size){Pageable pageable=PageRequest.of(Math.max(0,page),safeSize(size),Sort.by(Sort.Direction.ASC,"dueAt"));Page<TaskItem> result=tasks.findAllByOrganizationIdAndAssignedUserIdAndAssignedUserType(p.organizationId(),p.userId(),"CLIENT",pageable);return new PageResult(result.getContent().stream().map(TaskService::map).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());}
    @Transactional
    public TaskResponse clientTransition(ClientPrincipal p,UUID id,TransitionTaskRequest r,HttpServletRequest req){
        LockedTask locked=lockedWithWorkflowContext(p.organizationId(),id,r.version());
        TaskItem t=locked.task();
        if(!p.userId().equals(t.getAssignedUserId())||!"CLIENT".equals(t.getAssignedUserType()))throw notFound();
        if(t.getProjectId()!=null)clientAccess.requireProjectAccess(p.organizationId(),p.userId(),t.getProjectId());
        if(!(r.status()==TaskStatus.IN_PROGRESS||r.status()==TaskStatus.IN_REVIEW||r.status()==TaskStatus.COMPLETED)){
            throw new ApiException(HttpStatus.FORBIDDEN,"TASK_TRANSITION_NOT_ALLOWED","Client tasks can only be started, submitted for review, or completed.");
        }
        TaskStatus target=r.status();
        if(locked.workflowContext()!=null){
            if(locked.workflowContext().requiresReview()&&target==TaskStatus.COMPLETED){
                throw new ApiException(HttpStatus.FORBIDDEN,"TASK_REVIEW_REQUIRED","Submit this task for review; only the internal team can complete a review-required workflow task.");
            }
            if(!locked.workflowContext().requiresReview()&&target==TaskStatus.IN_REVIEW){
                target=TaskStatus.COMPLETED;
            }
        }
        transitionInternal(t,target,p.userId(),"CLIENT");
        tasks.saveAndFlush(t);
        syncWorkflow(t,p.userId(),WorkflowActorType.CLIENT);
        recordClient(p,t,"TASK_"+t.getStatus().name(),req);
        return map(t);
    }
    @Transactional public void createWorkflowTask(com.brainserve.onboarding.onboarding.application.service.OnboardingStepInitializer.StepCreated s){if(s.stepType()!=WorkflowStepType.MANUAL_TASK)return;String key="workflow-step:"+s.stepId();if(tasks.findByOrganizationIdAndSourceKey(s.organizationId(),key).isPresent())return;TaskItem t=new TaskItem(UUID.randomUUID(),s.organizationId(),s.projectId(),s.onboardingId(),s.stepId(),s.name(),s.description(),TaskType.WORKFLOW_GENERATED,TaskPriority.MEDIUM,null,null,s.assignedRoleId(),s.dueAt(),key,null,"SYSTEM",s.occurredAt());tasks.saveAndFlush(t);}
    private void validateScope(UUID org,UUID projectId,UUID onboardingId,UUID stepId){if(projectId!=null)projects.require(org,projectId);if((onboardingId!=null||stepId!=null)&&projectId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"TASK_SCOPE_INVALID","Onboarding or step tasks require a project scope.");if(stepId!=null){var locked=workflow.lockStepContext(org,onboardingId,stepId);if(!locked.projectId().equals(projectId))throw notFound();}}
    private TaskItem locked(UUID org,UUID id,long version){TaskItem t=tasks.findForUpdate(org,id).orElseThrow(TaskService::notFound);if(t.getVersion()!=version)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The task changed. Refresh and try again.");return t;}
    private LockedTask lockedWithWorkflowContext(UUID org,UUID id,long version){
        TaskItem snapshot=tasks.findByOrganizationIdAndId(org,id).orElseThrow(TaskService::notFound);
        OnboardingStepCommandService.LockedStep context=null;
        if(snapshot.getTaskType()==TaskType.WORKFLOW_GENERATED&&snapshot.getOnboardingId()!=null&&snapshot.getStepId()!=null){
            context=workflow.lockStepContext(org,snapshot.getOnboardingId(),snapshot.getStepId());
        }
        TaskItem t=locked(org,id,version);
        if(context!=null&&t.getProjectId()!=null&&!context.projectId().equals(t.getProjectId()))throw notFound();
        return new LockedTask(t,context);
    }
    private void transitionInternal(TaskItem t,TaskStatus status,UUID actor,String actorType){try{t.transition(status,actor,actorType,clock.instant());}catch(IllegalStateException|IllegalArgumentException ex){throw conflict(ex.getMessage());}}
    private void syncWorkflow(TaskItem t, UUID actor, WorkflowActorType actorType) {
        if (t.getStepId() == null || t.getTaskType() != TaskType.WORKFLOW_GENERATED) return;
        var context = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
        if (context.stepType() != WorkflowStepType.MANUAL_TASK) return;

        if (t.getStatus() == TaskStatus.IN_PROGRESS && context.status() == OnboardingStepStatus.AVAILABLE) {
            workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                    OnboardingStepStatus.IN_PROGRESS, actor, actorType);
            return;
        }

        if (t.getStatus() == TaskStatus.IN_REVIEW) {
            if (context.status() == OnboardingStepStatus.AVAILABLE) {
                workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                        OnboardingStepStatus.IN_PROGRESS, actor, actorType);
            }
            var current = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
            if (!current.requiresReview()) return;
            if (current.status() == OnboardingStepStatus.IN_PROGRESS) {
                workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                        OnboardingStepStatus.SUBMITTED, actor, actorType);
            }
            current = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
            if (current.status() == OnboardingStepStatus.SUBMITTED) {
                workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                        OnboardingStepStatus.UNDER_REVIEW, actor, actorType);
            }
            return;
        }

        if (t.getStatus() != TaskStatus.COMPLETED) return;
        var current = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
        if (current.status() == OnboardingStepStatus.AVAILABLE) {
            workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                    OnboardingStepStatus.IN_PROGRESS, actor, actorType);
            current = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
        }
        if (!current.requiresReview()) {
            if (current.status() == OnboardingStepStatus.IN_PROGRESS || current.status() == OnboardingStepStatus.SUBMITTED) {
                workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                        OnboardingStepStatus.COMPLETED, actor, actorType);
            }
            return;
        }
        if (current.status() == OnboardingStepStatus.IN_PROGRESS) {
            workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                    OnboardingStepStatus.SUBMITTED, actor, actorType);
            current = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
        }
        if (current.status() == OnboardingStepStatus.SUBMITTED) {
            workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                    OnboardingStepStatus.UNDER_REVIEW, actor, actorType);
            current = workflow.lockStepContext(t.getOrganizationId(), t.getOnboardingId(), t.getStepId());
        }
        if (current.status() == OnboardingStepStatus.UNDER_REVIEW) {
            workflow.transition(t.getOrganizationId(), t.getOnboardingId(), t.getStepId(),
                    OnboardingStepStatus.COMPLETED, actor, actorType);
        }
    }
    private void record(TenantPrincipal p,TaskItem t,String action,HttpServletRequest req){if(t.getProjectId()!=null)activity.record(p.organizationId(),null,t.getProjectId(),p.userId(),action,"TASK",t.getId(),t.getTitle(),Map.of("status",t.getStatus().name()));audit.record(p.organizationId(),p.userId(),action,"TASK",t.getId(),null,Map.of("status",t.getStatus().name()),req);recordOutbox(t,action);notifyAssignee(t,action);}
    private void recordClient(ClientPrincipal p,TaskItem t,String action,HttpServletRequest req){if(t.getProjectId()!=null)activity.recordClient(p.organizationId(),null,t.getProjectId(),p.userId(),action,"TASK",t.getId(),t.getTitle(),Map.of("status",t.getStatus().name()));audit.recordClient(p.organizationId(),p.userId(),action,"TASK",t.getId(),null,Map.of("status",t.getStatus().name()),req);recordOutbox(t,action);notifyAssignee(t,action);}
    private void recordOutbox(TaskItem t,String action){Map<String,Object> payload=new LinkedHashMap<>();payload.put("taskId",t.getId());if(t.getProjectId()!=null)payload.put("projectId",t.getProjectId());payload.put("status",t.getStatus().name());outbox.record(t.getOrganizationId(),action,"TASK",t.getId(),payload);}
    private void notifyAssignee(TaskItem t,String action){if(t.getAssignedUserId()==null||t.getAssignedUserType()==null)return;String route="CLIENT".equals(t.getAssignedUserType())?"/portal/tasks":"/app/tasks";notificationService.createForUser(t.getOrganizationId(),t.getAssignedUserId(),t.getAssignedUserType(),null,action,"TASK",t.getId(),t.getProjectId(),"Task update: "+t.getTitle(),"Task status: "+t.getStatus().name().replace('_',' ').toLowerCase(),route,"task:"+t.getId()+":"+action+":"+t.getVersion()+":"+t.getAssignedUserId(),List.of(NotificationChannel.IN_APP,NotificationChannel.EMAIL),false);}

    private static int safeSize(int size){return Math.min(Math.max(1,size),MAX_PAGE_SIZE);} private static TaskResponse map(TaskItem t){return new TaskResponse(t.getId(),t.getProjectId(),t.getOnboardingId(),t.getStepId(),t.getTitle(),t.getDescription(),t.getTaskType(),t.getStatus(),t.getPriority(),t.getAssignedUserId(),t.getAssignedUserType(),t.getAssignedRoleId(),t.getDueAt(),t.getCompletedAt(),t.getCreatedAt(),t.getUpdatedAt(),t.getVersion());}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested task was not found.");} private static ApiException conflict(String m){return new ApiException(HttpStatus.CONFLICT,"TASK_STATE_INVALID",m);}
    private record LockedTask(TaskItem task,OnboardingStepCommandService.LockedStep workflowContext){}
    public record PageResult(List<TaskResponse> items,int page,int size,long totalElements,int totalPages){}
}
