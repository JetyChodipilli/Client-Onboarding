package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.client.application.service.ClientLookupService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalDashboardResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalNextActionResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalProjectDetailResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalProjectSummaryResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientPortalStepResponse;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstanceDependency;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.domain.policy.OnboardingProgressPolicy;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceDependencyRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.project.application.service.ProjectPortalQueryService;
import com.brainserve.onboarding.servicecatalog.application.service.ServiceCatalogLookupService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, explicitly client-scoped portal projection. Internal-only workflow steps are filtered before mapping. */
@Service
public class ClientPortalService {
    private static final Set<OnboardingStepStatus> CLIENT_ACTION = Set.of(
            OnboardingStepStatus.AVAILABLE, OnboardingStepStatus.IN_PROGRESS, OnboardingStepStatus.NEEDS_REVISION);
    private static final Set<OnboardingStepStatus> TEAM_WAIT = Set.of(
            OnboardingStepStatus.SUBMITTED, OnboardingStepStatus.UNDER_REVIEW, OnboardingStepStatus.FAILED);
    private static final Set<OnboardingStepStatus> DONE = Set.of(OnboardingStepStatus.COMPLETED, OnboardingStepStatus.SKIPPED);
    private static final Set<com.brainserve.onboarding.workflow.domain.model.WorkflowStepType> SIMPLE_CLIENT_TYPES = Set.of(
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.WELCOME,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.INSTRUCTION,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.EXTERNAL_LINK,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.VIDEO_GUIDE,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.MEETING,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.CUSTOM);
    private static final Set<com.brainserve.onboarding.workflow.domain.model.WorkflowStepType> PORTAL_ACTION_TYPES = Set.of(
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.FORM,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.FILE_UPLOAD,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.PAYMENT,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.CONTRACT,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.PLATFORM_ACCESS,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.WELCOME,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.INSTRUCTION,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.EXTERNAL_LINK,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.VIDEO_GUIDE,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.MEETING,
            com.brainserve.onboarding.workflow.domain.model.WorkflowStepType.CUSTOM);

    private final ClientPortalAccessService access;
    private final ProjectPortalQueryService projects;
    private final ClientLookupService clients;
    private final ServiceCatalogLookupService services;
    private final OnboardingInstanceRepository onboardings;
    private final OnboardingStepInstanceRepository steps;
    private final OnboardingStepInstanceDependencyRepository dependencies;

    public ClientPortalService(ClientPortalAccessService access, ProjectPortalQueryService projects,
                               ClientLookupService clients, ServiceCatalogLookupService services,
                               OnboardingInstanceRepository onboardings, OnboardingStepInstanceRepository steps,
                               OnboardingStepInstanceDependencyRepository dependencies) {
        this.access=access; this.projects=projects; this.clients=clients; this.services=services;
        this.onboardings=onboardings; this.steps=steps; this.dependencies=dependencies;
    }

    @Transactional(readOnly=true)
    public ClientPortalDashboardResponse dashboard(ClientPrincipal principal) {
        List<ClientPortalAccessService.ProjectAccess> grants=access.activeProjectAccess(principal.organizationId(),principal.userId());
        Map<UUID, ClientPortalAccessService.ProjectAccess> grantByProject=grants.stream()
                .collect(Collectors.toMap(ClientPortalAccessService.ProjectAccess::projectId, Function.identity()));
        Set<UUID> projectIds=grantByProject.keySet();
        List<ProjectPortalQueryService.ProjectRef> projectRows=projects.findAll(principal.organizationId(),projectIds).stream()
                .sorted(Comparator.comparing(ProjectPortalQueryService.ProjectRef::name,String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProjectPortalQueryService.ProjectRef::id)).toList();
        if(projectRows.isEmpty()) return new ClientPortalDashboardResponse(principal.organizationName(),null,List.of());

        Map<UUID,String> clientNames=clients.names(principal.organizationId(),projectRows.stream().map(ProjectPortalQueryService.ProjectRef::clientId).collect(Collectors.toSet()));
        Map<UUID,String> serviceNames=services.names(principal.organizationId(),projectRows.stream().map(ProjectPortalQueryService.ProjectRef::serviceId).collect(Collectors.toSet()));
        Map<UUID,OnboardingInstance> onboardingByProject=onboardings.findAllByOrganizationIdAndProjectIdIn(principal.organizationId(),projectIds).stream()
                .collect(Collectors.toMap(OnboardingInstance::getProjectId,Function.identity()));
        Set<UUID> onboardingIds=onboardingByProject.values().stream().map(OnboardingInstance::getId).collect(Collectors.toSet());
        Map<UUID,List<OnboardingStepInstance>> stepsByOnboarding=groupSteps(principal.organizationId(),onboardingIds);
        Map<UUID,List<OnboardingStepInstanceDependency>> depsByOnboarding=groupDependencies(principal.organizationId(),onboardingIds);

        List<ClientPortalProjectSummaryResponse> summaries=new ArrayList<>();
        for(var project:projectRows){
            OnboardingInstance onboarding=onboardingByProject.get(project.id());
            summaries.add(summary(project,clientNames.getOrDefault(project.clientId(),"Client"),
                    serviceNames.getOrDefault(project.serviceId(),"Service"),grantByProject.get(project.id()).accessLevel(),onboarding,
                    onboarding==null?List.of():stepsByOnboarding.getOrDefault(onboarding.getId(),List.of()),
                    onboarding==null?List.of():depsByOnboarding.getOrDefault(onboarding.getId(),List.of())));
        }
        ClientPortalNextActionResponse primary=summaries.stream().map(ClientPortalProjectSummaryResponse::nextAction)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(ClientPortalNextActionResponse::dueAt,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ClientPortalNextActionResponse::title,String.CASE_INSENSITIVE_ORDER)).orElse(null);
        return new ClientPortalDashboardResponse(principal.organizationName(),primary,summaries);
    }

    @Transactional(readOnly=true)
    public ClientPortalProjectDetailResponse project(ClientPrincipal principal, UUID projectId) {
        var grant=access.requireProjectAccess(principal.organizationId(),principal.userId(),projectId);
        var project=projects.require(principal.organizationId(),projectId);
        OnboardingInstance onboarding=onboardings.findByOrganizationIdAndProjectId(principal.organizationId(),projectId).orElse(null);
        String clientName=clients.names(principal.organizationId(),Set.of(project.clientId())).getOrDefault(project.clientId(),"Client");
        String serviceName=services.names(principal.organizationId(),Set.of(project.serviceId())).getOrDefault(project.serviceId(),"Service");
        if(onboarding==null){
            return new ClientPortalProjectDetailResponse(project.id(),project.name(),clientName,serviceName,project.status(),null,null,
                    0,0,0,"OUR_TEAM",null,"Your project team is preparing onboarding.",
                    "Contact your project team if you need help.",List.of(),List.of(),List.of(),List.of());
        }
        List<OnboardingStepInstance> all=steps.findAllByOrganizationIdAndOnboardingIdOrderByDisplayOrderAscIdAsc(principal.organizationId(),onboarding.getId());
        List<OnboardingStepInstanceDependency> edges=dependencies.findAllByOrganizationIdAndOnboardingId(principal.organizationId(),onboarding.getId());
        var progress=progress(all);
        if (grant.accessLevel() == ClientProjectAccessLevel.CLIENT_MEMBER) {
            return new ClientPortalProjectDetailResponse(project.id(),project.name(),clientName,serviceName,project.status(),onboarding.getId(),onboarding.getStatus(),
                    progress.percentage(),progress.completed(),progress.total(),"OUR_TEAM",null,
                    "No onboarding actions are assigned to your client-member account.",
                    "Ask your client administrator or project team if you need responsibility for a requirement.",
                    List.of(),List.of(),List.of(),List.of());
        }
        ClientPortalNextActionResponse next=nextAction(project.id(),onboarding,all);
        String waiting=waitingFor(onboarding,next);
        Map<UUID,OnboardingStepInstance> byId=all.stream().collect(Collectors.toMap(OnboardingStepInstance::getId,Function.identity()));
        Map<UUID,List<UUID>> depIds=edges.stream().collect(Collectors.groupingBy(OnboardingStepInstanceDependency::getStepInstanceId,
                Collectors.mapping(OnboardingStepInstanceDependency::getDependsOnStepInstanceId,Collectors.toList())));
        List<ClientPortalStepResponse> visible=all.stream().filter(OnboardingStepInstance::isClientVisible)
                .map(step->mapStep(step,byId,depIds.getOrDefault(step.getId(),List.of()))).toList();
        List<ClientPortalStepResponse> your=visible.stream().filter(step->CLIENT_ACTION.contains(step.status())&&PORTAL_ACTION_TYPES.contains(step.stepType())).toList();
        List<ClientPortalStepResponse> team=visible.stream().filter(step->TEAM_WAIT.contains(step.status())
                || (CLIENT_ACTION.contains(step.status())&&!PORTAL_ACTION_TYPES.contains(step.stepType()))).toList();
        List<ClientPortalStepResponse> locked=visible.stream().filter(step->step.status()==OnboardingStepStatus.LOCKED).toList();
        List<ClientPortalStepResponse> done=visible.stream().filter(step->DONE.contains(step.status())).toList();
        return new ClientPortalProjectDetailResponse(project.id(),project.name(),clientName,serviceName,project.status(),onboarding.getId(),onboarding.getStatus(),
                progress.percentage(),progress.completed(),progress.total(),waiting,next,blockingReason(onboarding,next,all),
                "Contact your project team if you need help with any requirement.",your,team,locked,done);
    }

    private ClientPortalProjectSummaryResponse summary(ProjectPortalQueryService.ProjectRef project,String clientName,String serviceName,
                                                       ClientProjectAccessLevel accessLevel, OnboardingInstance onboarding,List<OnboardingStepInstance> all,
                                                       List<OnboardingStepInstanceDependency> edges){
        if(onboarding==null) return new ClientPortalProjectSummaryResponse(project.id(),project.name(),clientName,serviceName,project.status(),
                null,null,0,0,0,"OUR_TEAM",null,null);
        var progress=progress(all);
        ClientPortalNextActionResponse next=accessLevel == ClientProjectAccessLevel.CLIENT_ADMIN ? nextAction(project.id(),onboarding,all) : null;
        Instant due=accessLevel == ClientProjectAccessLevel.CLIENT_ADMIN ? all.stream().filter(OnboardingStepInstance::isClientVisible)
                .filter(step->!DONE.contains(step.getStatus())&&step.getStatus()!=OnboardingStepStatus.CANCELLED)
                .map(OnboardingStepInstance::getDueAt).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null) : null;
        return new ClientPortalProjectSummaryResponse(project.id(),project.name(),clientName,serviceName,project.status(),onboarding.getId(),
                onboarding.getStatus(),progress.percentage(),progress.completed(),progress.total(),waitingFor(onboarding,next),next,due);
    }

    private static OnboardingProgressPolicy.Progress progress(Collection<OnboardingStepInstance> all){
        return OnboardingProgressPolicy.calculate(all.stream().map(step->new OnboardingProgressPolicy.Requirement(
                step.getStepType(),step.isRequired(),step.isBlocking(),step.getStatus())).toList());
    }

    private static ClientPortalNextActionResponse nextAction(UUID projectId,OnboardingInstance onboarding,List<OnboardingStepInstance> all){
        if(Set.of(OnboardingStatus.PAUSED,OnboardingStatus.CANCELLED,OnboardingStatus.EXPIRED,OnboardingStatus.COMPLETED,OnboardingStatus.APPROVED)
                .contains(onboarding.getStatus())) return null;
        return all.stream().filter(OnboardingStepInstance::isClientVisible).filter(step->CLIENT_ACTION.contains(step.getStatus()))
                .filter(step->PORTAL_ACTION_TYPES.contains(step.getStepType()))
                .sorted(Comparator.comparingInt(OnboardingStepInstance::getDisplayOrder).thenComparing(OnboardingStepInstance::getId))
                .map(step->new ClientPortalNextActionResponse(projectId,step.getId(),actionTitle(step),
                        actionDescription(step),step.getDueAt(),step.getStepType().name())).findFirst().orElse(null);
    }

    private static String actionTitle(OnboardingStepInstance step){
        return switch(step.getStatus()){
            case NEEDS_REVISION -> "Revise " + step.getName();
            case IN_PROGRESS -> "Continue " + step.getName();
            default -> step.getName();
        };
    }
    private static String actionDescription(OnboardingStepInstance step){
        if(step.getDescription()!=null&&!step.getDescription().isBlank()) return step.getDescription();
        return step.getStatus()==OnboardingStepStatus.NEEDS_REVISION?"A revision is required before onboarding can continue.":"This requirement is ready for you.";
    }
    private static String waitingFor(OnboardingInstance onboarding,ClientPortalNextActionResponse next){
        if(onboarding.getStatus()==OnboardingStatus.PAUSED) return "PAUSED";
        if(onboarding.getStatus()==OnboardingStatus.COMPLETED) return "COMPLETE";
        if(onboarding.getStatus()==OnboardingStatus.CANCELLED) return "CANCELLED";
        if(onboarding.getStatus()==OnboardingStatus.EXPIRED) return "EXPIRED";
        return next!=null?"YOU":"OUR_TEAM";
    }
    private static String blockingReason(OnboardingInstance onboarding,ClientPortalNextActionResponse next,List<OnboardingStepInstance> all){
        if(next!=null) return "Your next action is “"+next.title()+"”.";
        return switch(onboarding.getStatus()){
            case PAUSED -> "Onboarding is paused. Your project team will let you know when work resumes.";
            case AWAITING_INTERNAL_REVIEW, APPROVED -> "Your required actions are complete. Waiting for our team to finish review.";
            case COMPLETED -> "Onboarding is complete.";
            case CANCELLED -> "Onboarding has been cancelled.";
            case EXPIRED -> "Onboarding has expired. Contact your project team for help.";
            default -> all.stream().anyMatch(step->!step.isClientVisible()&&!DONE.contains(step.getStatus()))
                    ?"Waiting for our team to complete an internal requirement."
                    :"No client action is required right now.";
        };
    }

    private static ClientPortalStepResponse mapStep(OnboardingStepInstance step,Map<UUID,OnboardingStepInstance> byId,List<UUID> dependencyIds){
        String lockedReason=null;
        if(step.getStatus()==OnboardingStepStatus.LOCKED){
            List<OnboardingStepInstance> deps=dependencyIds.stream().map(byId::get).filter(java.util.Objects::nonNull)
                    .filter(dep->dep.getStatus()!=OnboardingStepStatus.COMPLETED).toList();
            List<String> visible=deps.stream().filter(OnboardingStepInstance::isClientVisible).map(OnboardingStepInstance::getName).toList();
            boolean internal=deps.stream().anyMatch(dep->!dep.isClientVisible());
            if(!visible.isEmpty()) lockedReason="Complete " + String.join(", ",visible) + " first.";
            else if(internal) lockedReason="Waiting for our team to complete an internal prerequisite.";
            else lockedReason="This requirement will unlock when its prerequisite is complete.";
        }
        String label=CLIENT_ACTION.contains(step.getStatus())&&PORTAL_ACTION_TYPES.contains(step.getStepType())
                ? (SIMPLE_CLIENT_TYPES.contains(step.getStepType())
                    ? (step.isRequiresReview() ? "Submit for review" : "Mark requirement complete")
                    : actionTitle(step)) : null;
        return new ClientPortalStepResponse(step.getId(),step.getStepKey(),step.getName(),step.getDescription(),step.getStepType(),
                step.getStatus(),step.isRequired(),step.isBlocking(),step.isRequiresReview(),step.getDueAt(),lockedReason,label);
    }

    private Map<UUID,List<OnboardingStepInstance>> groupSteps(UUID organizationId,Collection<UUID> onboardingIds){
        if(onboardingIds.isEmpty()) return Map.of();
        return steps.findAllByOrganizationIdAndOnboardingIdInOrderByOnboardingIdAscDisplayOrderAscIdAsc(organizationId,onboardingIds).stream()
                .collect(Collectors.groupingBy(OnboardingStepInstance::getOnboardingId,LinkedHashMap::new,Collectors.toList()));
    }
    private Map<UUID,List<OnboardingStepInstanceDependency>> groupDependencies(UUID organizationId,Collection<UUID> onboardingIds){
        if(onboardingIds.isEmpty()) return Map.of();
        return dependencies.findAllByOrganizationIdAndOnboardingIdIn(organizationId,onboardingIds).stream()
                .collect(Collectors.groupingBy(OnboardingStepInstanceDependency::getOnboardingId,HashMap::new,Collectors.toList()));
    }
}
