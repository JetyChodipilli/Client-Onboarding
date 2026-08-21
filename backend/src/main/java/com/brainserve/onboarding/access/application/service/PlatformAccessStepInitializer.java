package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.access.domain.model.*;
import com.brainserve.onboarding.access.infrastructure.persistence.PlatformAccessRequestRepository;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepInitializer;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformAccessStepInitializer implements OnboardingStepInitializer {
    private final PlatformAccessCatalogService catalog;
    private final PlatformAccessRequestRepository requests;
    public PlatformAccessStepInitializer(PlatformAccessCatalogService catalog,PlatformAccessRequestRepository requests){this.catalog=catalog;this.requests=requests;}
    @Override public boolean supports(WorkflowStepType stepType){return stepType==WorkflowStepType.PLATFORM_ACCESS;}
    @Override public void initialize(StepCreated step){
        if(!step.requiresReview()) throw new ApiException(HttpStatus.CONFLICT,"PLATFORM_ACCESS_REVIEW_REQUIRED","Platform access steps must require internal verification.");
        UUID guideVersionId;
        try{guideVersionId=UUID.fromString(step.configuration().path("platformAccessTypeVersionId").asText());}catch(IllegalArgumentException ex){throw new ApiException(HttpStatus.CONFLICT,"PLATFORM_ACCESS_CONFIGURATION_INVALID","The platform access guide reference is invalid.");}
        var published=catalog.requirePublished(step.organizationId(),guideVersionId);
        PlatformAccessRequestStatus initial=step.status()==OnboardingStepStatus.AVAILABLE?PlatformAccessRequestStatus.REQUESTED:PlatformAccessRequestStatus.NOT_STARTED;
        requests.saveAndFlush(new PlatformAccessRequest(UUID.randomUUID(),step.organizationId(),step.projectId(),step.clientId(),step.onboardingId(),step.stepId(),published.type(),published.version(),initial,step.actorId(),PlatformAccessActorType.INTERNAL,step.occurredAt()));
    }
}
