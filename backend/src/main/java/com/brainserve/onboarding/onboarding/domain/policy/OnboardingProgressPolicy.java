package com.brainserve.onboarding.onboarding.domain.policy;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.Collection;
import java.util.EnumSet;

/** PRD progress rule: applicable completion-relevant steps only; optional informational items may be excluded. */
public final class OnboardingProgressPolicy {
    private static final EnumSet<WorkflowStepType> INFORMATIONAL = EnumSet.of(
            WorkflowStepType.WELCOME, WorkflowStepType.INSTRUCTION,
            WorkflowStepType.EXTERNAL_LINK, WorkflowStepType.VIDEO_GUIDE);

    private OnboardingProgressPolicy() {}

    public static Progress calculate(Collection<Requirement> applicable) {
        var relevant = applicable.stream().filter(OnboardingProgressPolicy::completionRelevant).toList();
        long completed = relevant.stream().filter(OnboardingProgressPolicy::satisfied).count();
        int percentage = relevant.isEmpty() ? 100 : (int) Math.floor(completed * 100.0 / relevant.size());
        return new Progress(percentage, completed, relevant.size());
    }

    private static boolean completionRelevant(Requirement step) {
        return step.required() || step.blocking() || !INFORMATIONAL.contains(step.stepType());
    }

    private static boolean satisfied(Requirement step) {
        return step.status() == OnboardingStepStatus.COMPLETED || step.status() == OnboardingStepStatus.SKIPPED;
    }

    public record Requirement(WorkflowStepType stepType, boolean required, boolean blocking, OnboardingStepStatus status) {}
    public record Progress(int percentage, long completed, int total) {}
}
