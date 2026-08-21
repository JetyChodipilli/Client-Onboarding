package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.access.domain.model.*;
import com.brainserve.onboarding.access.infrastructure.persistence.PlatformAccessRequestRepository;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepTransitionListener;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import org.springframework.stereotype.Service;

@Service
public class PlatformAccessStepTransitionListener implements OnboardingStepTransitionListener {
    private final PlatformAccessRequestRepository requests;
    public PlatformAccessStepTransitionListener(PlatformAccessRequestRepository requests){this.requests=requests;}
    @Override public boolean supports(WorkflowStepType stepType){return stepType==WorkflowStepType.PLATFORM_ACCESS;}
    @Override public void onTransition(StepTransition transition){
        if(transition.after()!=OnboardingStepStatus.AVAILABLE) return;
        PlatformAccessRequest request=requests.findByStepForUpdate(transition.organizationId(),transition.stepId()).orElse(null);
        if(request!=null && request.getStatus()==PlatformAccessRequestStatus.NOT_STARTED){request.requested(transition.actorId(),map(transition.actorType()),transition.occurredAt());requests.saveAndFlush(request);}
    }
    private static PlatformAccessActorType map(com.brainserve.onboarding.onboarding.application.service.WorkflowActorType type){return switch(type){case CLIENT->PlatformAccessActorType.CLIENT;case SYSTEM->PlatformAccessActorType.SYSTEM;case INTERNAL->PlatformAccessActorType.INTERNAL;};}
}
