package com.brainserve.clientonboarding.onboarding.application;

import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.onboarding.domain.model.*;
import com.brainserve.clientonboarding.onboarding.domain.repository.OnboardingRepository;
import com.brainserve.clientonboarding.project.application.ProjectWorkflowPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for feature handlers. Caller authorizes the user and owns the transaction. */
@Service
public class StepExecutionService {
    private final OnboardingRepository repository;
    private final ProjectWorkflowPort projects;
    public StepExecutionService(OnboardingRepository repository, ProjectWorkflowPort projects) { this.repository=repository; this.projects=projects; }
    public Context read(UUID org, UUID stepId) {
        var step = repository.findStep(org,stepId).orElseThrow(this::notFound);
        var onboarding = repository.findById(org,step.onboardingId()).orElseThrow(this::notFound);
        var project = projects.requireProject(org,onboarding.projectId());
        return new Context(onboarding,step,project.status().name());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Context lockActive(UUID org, UUID stepId) {
        var initial = read(org,stepId);
        projects.lockOnboardingProject(org,initial.onboarding().projectId());
        var context = read(org,stepId);
        if (context.onboarding().status()!=OnboardingInstance.Status.IN_PROGRESS) throw new DomainException("ONBOARDING_NOT_ACTIVE","This onboarding is not accepting updates.",HttpStatus.CONFLICT);
        return context;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void transition(Context context, OnboardingStepInstance.Status target, UUID actor, Instant now) {
        var step=context.step(); var onboarding=context.onboarding();
        if (step.status()!=target && !repository.updateStepStatus(step.organizationId(),step.id(),step.status(),target,step.version(),actor,now)) throw conflict();
        repository.refreshAvailability(step.organizationId(),onboarding.id(),actor,now);
        boolean ready=ReadinessPolicy.ready(repository.findSteps(step.organizationId(),onboarding.id()));
        if (!repository.updateReadiness(step.organizationId(),onboarding.id(),ready,onboarding.version(),actor,now)) throw conflict();
    }
    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND","Requested resource was not found.",HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT","The resource changed. Refresh and try again.",HttpStatus.CONFLICT); }
    public record Context(OnboardingInstance onboarding,OnboardingStepInstance step,String projectStatus) { }
}
