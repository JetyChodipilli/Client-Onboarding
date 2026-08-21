package com.brainserve.onboarding.workflow.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@IdClass(WorkflowStepDependency.Key.class)
@Table(name = "onboarding_step_dependencies", schema = "client_onboarding")
public class WorkflowStepDependency {
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "template_version_id", nullable = false) private UUID templateVersionId;
    @Id @Column(name = "step_id", nullable = false) private UUID stepId;
    @Id @Column(name = "depends_on_step_id", nullable = false) private UUID dependsOnStepId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;

    protected WorkflowStepDependency() {}

    public WorkflowStepDependency(UUID organizationId, UUID templateVersionId, UUID stepId, UUID dependsOnStepId,
                                  UUID actorId, Instant now) {
        if (stepId.equals(dependsOnStepId)) throw new IllegalArgumentException("A step cannot depend on itself");
        this.organizationId = organizationId;
        this.templateVersionId = templateVersionId;
        this.stepId = stepId;
        this.dependsOnStepId = dependsOnStepId;
        this.createdAt = now;
        this.createdBy = actorId;
    }

    public UUID getStepId() { return stepId; }
    public UUID getDependsOnStepId() { return dependsOnStepId; }

    public static final class Key implements Serializable {
        private UUID stepId;
        private UUID dependsOnStepId;
        public Key() {}
        public Key(UUID stepId, UUID dependsOnStepId) { this.stepId = stepId; this.dependsOnStepId = dependsOnStepId; }
        @Override public boolean equals(Object o) { return o instanceof Key other && Objects.equals(stepId, other.stepId) && Objects.equals(dependsOnStepId, other.dependsOnStepId); }
        @Override public int hashCode() { return Objects.hash(stepId, dependsOnStepId); }
    }
}
