package com.brainserve.onboarding.onboarding.api.response;

import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record ClientPortalProjectSummaryResponse(UUID projectId, String projectName, String clientName, String serviceName,
        ProjectStatus projectStatus, UUID onboardingId, OnboardingStatus onboardingStatus, int progressPercent,
        long completedRequirements, int totalRequirements, String waitingFor,
        ClientPortalNextActionResponse nextAction, Instant nearestDueAt) {}
