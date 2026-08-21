package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import java.util.List;
import java.util.UUID;

public record ClientPortalProjectDetailResponse(UUID projectId, String projectName, String clientName, String serviceName,
        ProjectStatus projectStatus, UUID onboardingId, OnboardingStatus onboardingStatus, int progressPercent,
        long completedRequirements, int totalRequirements, String waitingFor, ClientPortalNextActionResponse nextAction,
        String blockingReason, String helpText, List<ClientPortalStepResponse> yourAction,
        List<ClientPortalStepResponse> waitingForOurTeam, List<ClientPortalStepResponse> locked,
        List<ClientPortalStepResponse> completed) {
    public ClientPortalProjectDetailResponse {
        yourAction=List.copyOf(yourAction); waitingForOurTeam=List.copyOf(waitingForOurTeam);
        locked=List.copyOf(locked); completed=List.copyOf(completed);
    }
}
