package com.brainserve.clientonboarding.project.application;

import com.brainserve.clientonboarding.project.domain.model.ProjectRecord;
import java.time.Instant;
import java.util.UUID;

public interface ProjectWorkflowPort {
    ProjectRecord requireProject(UUID organizationId, UUID projectId);
    void beginOnboarding(UUID organizationId, UUID projectId, long version, UUID actorId, Instant now);
}
