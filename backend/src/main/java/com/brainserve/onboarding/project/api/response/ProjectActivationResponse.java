package com.brainserve.onboarding.project.api.response;

import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import java.util.UUID;

public record ProjectActivationResponse(UUID id, ProjectStatus status, long version) {}
