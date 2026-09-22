package com.brainserve.clientonboarding.portal.domain.model;

import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.util.Set;

/** Client permissions are narrower than the internal workflow state machine. */
public final class ClientStepPolicy {
    private ClientStepPolicy() { }

    public static boolean informational(TemplateStep.StepType type) {
        return Set.of(TemplateStep.StepType.WELCOME, TemplateStep.StepType.INSTRUCTION,
                TemplateStep.StepType.EXTERNAL_LINK, TemplateStep.StepType.VIDEO_GUIDE).contains(type);
    }

    public static boolean actionable(OnboardingStepInstance.Status status) {
        return Set.of(OnboardingStepInstance.Status.AVAILABLE, OnboardingStepInstance.Status.IN_PROGRESS,
                OnboardingStepInstance.Status.NEEDS_REVISION).contains(status);
    }

    public static boolean permits(OnboardingStepInstance.Status current, OnboardingStepInstance.Status target,
                                  boolean requiresReview) {
        return switch (current) {
            case AVAILABLE, NEEDS_REVISION -> target == OnboardingStepInstance.Status.IN_PROGRESS;
            case IN_PROGRESS -> target == (requiresReview ? OnboardingStepInstance.Status.SUBMITTED
                    : OnboardingStepInstance.Status.COMPLETED);
            default -> false;
        };
    }
}
