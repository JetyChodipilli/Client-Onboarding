package com.brainserve.onboarding.forms.infrastructure.persistence;

import com.brainserve.onboarding.forms.domain.model.FormTemplateVersion;
import com.brainserve.onboarding.forms.domain.model.FormVersionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormTemplateVersionRepository extends JpaRepository<FormTemplateVersion, UUID> {
    Optional<FormTemplateVersion> findByOrganizationIdAndId(UUID organizationId, UUID id);
    List<FormTemplateVersion> findAllByOrganizationIdAndFormIdOrderByVersionNumberDesc(UUID organizationId, UUID formId);
    List<FormTemplateVersion> findAllByOrganizationIdAndFormIdIn(UUID organizationId, Collection<UUID> formIds);
    Optional<FormTemplateVersion> findFirstByOrganizationIdAndFormIdAndStatusOrderByVersionNumberDesc(UUID organizationId, UUID formId, FormVersionStatus status);
    boolean existsByOrganizationIdAndFormIdAndStatus(UUID organizationId, UUID formId, FormVersionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from FormTemplateVersion v where v.organizationId=:org and v.id=:id")
    Optional<FormTemplateVersion> findForUpdate(@Param("org") UUID organizationId, @Param("id") UUID id);
}
