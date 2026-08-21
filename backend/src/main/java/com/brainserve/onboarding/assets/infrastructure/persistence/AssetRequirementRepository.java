package com.brainserve.onboarding.assets.infrastructure.persistence;

import com.brainserve.onboarding.assets.domain.model.AssetRequirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AssetRequirementRepository extends JpaRepository<AssetRequirement, UUID> {
    Optional<AssetRequirement> findByOrganizationIdAndId(UUID organizationId, UUID id);
    List<AssetRequirement> findAllByOrganizationIdAndIdIn(UUID organizationId, java.util.Collection<UUID> ids);
    Optional<AssetRequirement> findByOrganizationIdAndStepInstanceIdAndRequirementKey(UUID organizationId, UUID stepInstanceId, String requirementKey);
    List<AssetRequirement> findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(UUID organizationId, UUID projectId);
    List<AssetRequirement> findAllByOrganizationIdAndFormSubmissionIdAndFormFieldIdOrderByCreatedAtDescIdDesc(
            UUID organizationId, UUID formSubmissionId, UUID formFieldId);
    List<AssetRequirement> findAllByOrganizationIdAndStepInstanceIdAndFormFieldIdOrderByCreatedAtDescIdDesc(
            UUID organizationId, UUID stepInstanceId, UUID formFieldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssetRequirement r where r.organizationId=:organizationId and r.stepInstanceId=:stepInstanceId and r.requirementKey=:requirementKey")
    Optional<AssetRequirement> findForUpdate(UUID organizationId, UUID stepInstanceId, String requirementKey);
}
