package com.brainserve.onboarding.project.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddProjectMemberRequest(
        @NotNull UUID organizationUserId,
        @Size(max = 120) String responsibility) {}
