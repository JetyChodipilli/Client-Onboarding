package com.brainserve.onboarding.tasks.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="tasks", schema="client_onboarding")
public class TaskItem {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="project_id") private UUID projectId;
    @Column(name="onboarding_id") private UUID onboardingId;
    @Column(name="step_id") private UUID stepId;
    @Column(nullable=false,length=220) private String title;
    @Column(length=4000) private String description;
    @Enumerated(EnumType.STRING) @Column(name="task_type",nullable=false,length=32) private TaskType taskType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private TaskStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private TaskPriority priority;
    @Column(name="assigned_user_id") private UUID assignedUserId;
    @Column(name="assigned_user_type",length=16) private String assignedUserType;
    @Column(name="assigned_role_id") private UUID assignedRoleId;
    @Column(name="due_at") private Instant dueAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="source_key",length=180) private String sourceKey;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="created_by_type",nullable=false,length=16) private String createdByType;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="updated_by") private UUID updatedBy;
    @Column(name="updated_by_type",nullable=false,length=16) private String updatedByType;
    @Version private long version;
    protected TaskItem() {}
    public TaskItem(UUID id, UUID org, UUID projectId, UUID onboardingId, UUID stepId, String title, String description,
                    TaskType type, TaskPriority priority, UUID assignedUserId, String assignedUserType, UUID assignedRoleId,
                    Instant dueAt, String sourceKey, UUID actorId, String actorType, Instant now) {
        this.id=id; this.organizationId=org; this.projectId=projectId; this.onboardingId=onboardingId; this.stepId=stepId;
        this.title=text(title,220,"Task title"); this.description=optional(description,4000); this.taskType=type;
        this.status=TaskStatus.TODO; this.priority=priority==null?TaskPriority.MEDIUM:priority; this.assignedUserId=assignedUserId;
        this.assignedUserType=assignedUserId==null?null:requireActorType(assignedUserType,false); validateAssignment(assignedUserId, assignedRoleId); this.assignedRoleId=assignedRoleId;
        this.dueAt=dueAt; this.sourceKey=optional(sourceKey,180); this.createdAt=now; this.updatedAt=now;
        String normalizedActorType=requireActorType(actorType,true);
        if(!"SYSTEM".equals(normalizedActorType)&&actorId==null)throw new IllegalArgumentException("Actor id is required");
        this.createdBy="SYSTEM".equals(normalizedActorType)?null:actorId; this.updatedBy=this.createdBy;
        this.createdByType=normalizedActorType; this.updatedByType=normalizedActorType;
    }
    public void edit(String title,String description,TaskPriority priority,UUID assignedUserId,String assignedUserType,UUID assignedRoleId,Instant dueAt,UUID actorId,String actorType,Instant now){
        if(status==TaskStatus.COMPLETED||status==TaskStatus.CANCELLED) throw new IllegalStateException("Completed or cancelled tasks cannot be edited");
        this.title=text(title,220,"Task title"); this.description=optional(description,4000); this.priority=priority==null?this.priority:priority;
        this.assignedUserId=assignedUserId; this.assignedUserType=assignedUserId==null?null:requireActorType(assignedUserType,false);
        validateAssignment(assignedUserId, assignedRoleId); this.assignedRoleId=assignedRoleId; this.dueAt=dueAt; touch(actorId,actorType,now);
    }
    public void transition(TaskStatus target,UUID actorId,String actorType,Instant now){
        if(!TaskStateMachine.canTransition(status,target)) throw new IllegalStateException("Task cannot transition from "+status+" to "+target);
        status=target; completedAt=target==TaskStatus.COMPLETED?now:null; touch(actorId,actorType,now);
    }
    public void reopenFromFinalReview(UUID actorId,Instant now){
        if(status!=TaskStatus.COMPLETED) throw new IllegalStateException("Only completed workflow tasks can be reopened from final review");
        status=TaskStatus.IN_PROGRESS; completedAt=null; touch(actorId,"INTERNAL",now);
    }
    private void touch(UUID actorId,String actorType,Instant now){String type=requireActorType(actorType,true);updatedBy="SYSTEM".equals(type)?null:actorId;if(!"SYSTEM".equals(type)&&actorId==null)throw new IllegalArgumentException("Actor id is required");updatedByType=type;updatedAt=now;}
    private static void validateAssignment(UUID userId, UUID roleId){if(userId!=null&&roleId!=null)throw new IllegalArgumentException("Assign a task to either a user or a role, not both");}
    private static String requireActorType(String value,boolean system){if(value==null)throw new IllegalArgumentException("Actor type is required");String v=value.trim().toUpperCase(java.util.Locale.ROOT);if(!(v.equals("INTERNAL")||v.equals("CLIENT")||(system&&v.equals("SYSTEM"))))throw new IllegalArgumentException("Invalid actor type");return v;}
    private static String text(String v,int max,String label){if(v==null||v.isBlank())throw new IllegalArgumentException(label+" is required");String n=v.trim();if(n.length()>max)throw new IllegalArgumentException(label+" is too long");return n;}
    private static String optional(String v,int max){if(v==null||v.isBlank())return null;String n=v.trim();if(n.length()>max)throw new IllegalArgumentException("Value is too long");return n;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getProjectId(){return projectId;}
    public UUID getOnboardingId(){return onboardingId;} public UUID getStepId(){return stepId;} public String getTitle(){return title;}
    public String getDescription(){return description;} public TaskType getTaskType(){return taskType;} public TaskStatus getStatus(){return status;}
    public TaskPriority getPriority(){return priority;} public UUID getAssignedUserId(){return assignedUserId;} public String getAssignedUserType(){return assignedUserType;}
    public UUID getAssignedRoleId(){return assignedRoleId;} public Instant getDueAt(){return dueAt;} public Instant getCompletedAt(){return completedAt;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;} public String getSourceKey(){return sourceKey;}
}
