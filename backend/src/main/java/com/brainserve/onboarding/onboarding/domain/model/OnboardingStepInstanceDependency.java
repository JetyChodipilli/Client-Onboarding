package com.brainserve.onboarding.onboarding.domain.model;

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
@IdClass(OnboardingStepInstanceDependency.Key.class)
@Table(name = "onboarding_step_instance_dependencies", schema = "client_onboarding")
public class OnboardingStepInstanceDependency {
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "onboarding_id", nullable = false) private UUID onboardingId;
    @Id @Column(name = "step_instance_id", nullable = false) private UUID stepInstanceId;
    @Id @Column(name = "depends_on_step_instance_id", nullable = false) private UUID dependsOnStepInstanceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected OnboardingStepInstanceDependency() {}

    public OnboardingStepInstanceDependency(UUID organizationId, UUID onboardingId, UUID stepInstanceId,
                                            UUID dependsOnStepInstanceId, Instant now) {
        this.organizationId = organizationId;
        this.onboardingId = onboardingId;
        this.stepInstanceId = stepInstanceId;
        this.dependsOnStepInstanceId = dependsOnStepInstanceId;
        this.createdAt = now;
    }

    public UUID getOnboardingId() { return onboardingId; }
    public UUID getStepInstanceId() { return stepInstanceId; }
    public UUID getDependsOnStepInstanceId() { return dependsOnStepInstanceId; }

    public static final class Key implements Serializable {
        private UUID stepInstanceId;
        private UUID dependsOnStepInstanceId;
        public Key() {}
        public Key(UUID stepInstanceId, UUID dependsOnStepInstanceId) { this.stepInstanceId = stepInstanceId; this.dependsOnStepInstanceId = dependsOnStepInstanceId; }
        @Override public boolean equals(Object o) { return o instanceof Key other && Objects.equals(stepInstanceId, other.stepInstanceId) && Objects.equals(dependsOnStepInstanceId, other.dependsOnStepInstanceId); }
        @Override public int hashCode() { return Objects.hash(stepInstanceId, dependsOnStepInstanceId); }
    }
}
