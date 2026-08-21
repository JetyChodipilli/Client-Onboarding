package com.brainserve.onboarding.forms.infrastructure.persistence;

import com.brainserve.onboarding.forms.domain.model.FormSubmission;
import com.brainserve.onboarding.forms.domain.model.FormSubmissionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {
    Optional<FormSubmission> findByOrganizationIdAndId(UUID organizationId, UUID id);
    Optional<FormSubmission> findFirstByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(UUID organizationId, UUID stepId);
    Optional<FormSubmission> findByOrganizationIdAndStepInstanceIdAndStatus(UUID organizationId, UUID stepId, FormSubmissionStatus status);
    List<FormSubmission> findAllByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(UUID organizationId, UUID stepId);
    Page<FormSubmission> findAllByOrganizationIdAndStatus(UUID organizationId, FormSubmissionStatus status, Pageable pageable);
    Page<FormSubmission> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FormSubmission s where s.organizationId=:org and s.id=:id")
    Optional<FormSubmission> findForUpdate(@Param("org") UUID organizationId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FormSubmission s where s.organizationId=:org and s.stepInstanceId=:stepId and s.status=:status")
    Optional<FormSubmission> findForUpdateByStepAndStatus(@Param("org") UUID organizationId,
                                                           @Param("stepId") UUID stepId,
                                                           @Param("status") FormSubmissionStatus status);
}
