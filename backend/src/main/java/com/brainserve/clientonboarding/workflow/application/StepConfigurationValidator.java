package com.brainserve.clientonboarding.workflow.application;

import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.util.UUID;

/** Feature-owned validation invoked before publishing or snapshotting a workflow. */
public interface StepConfigurationValidator {
    void validate(UUID organizationId, TemplateStep step);
}
