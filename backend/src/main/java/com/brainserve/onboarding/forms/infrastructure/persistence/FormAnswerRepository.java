package com.brainserve.onboarding.forms.infrastructure.persistence;

import com.brainserve.onboarding.forms.domain.model.FormAnswer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormAnswerRepository extends JpaRepository<FormAnswer, UUID> {
    List<FormAnswer> findAllByOrganizationIdAndSubmissionId(UUID organizationId, UUID submissionId);
    List<FormAnswer> findAllByOrganizationIdAndSubmissionIdIn(UUID organizationId, Collection<UUID> submissionIds);
    Optional<FormAnswer> findByOrganizationIdAndSubmissionIdAndFieldId(UUID organizationId, UUID submissionId, UUID fieldId);
    void deleteAllByOrganizationIdAndSubmissionId(UUID organizationId, UUID submissionId);
}
