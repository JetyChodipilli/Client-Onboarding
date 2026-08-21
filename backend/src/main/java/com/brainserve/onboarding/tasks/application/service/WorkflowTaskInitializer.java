package com.brainserve.onboarding.tasks.application.service;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepInitializer;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import org.springframework.stereotype.Component;
@Component
public class WorkflowTaskInitializer implements OnboardingStepInitializer {
    private final TaskService tasks; public WorkflowTaskInitializer(TaskService tasks){this.tasks=tasks;}
    @Override public boolean supports(WorkflowStepType type){return type==WorkflowStepType.MANUAL_TASK;}
    @Override public void initialize(StepCreated step){tasks.createWorkflowTask(step);}
}
