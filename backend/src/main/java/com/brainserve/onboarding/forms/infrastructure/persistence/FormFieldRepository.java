package com.brainserve.onboarding.forms.infrastructure.persistence;

import com.brainserve.onboarding.forms.domain.model.FormField;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormFieldRepository extends JpaRepository<FormField, UUID> {
    List<FormField> findAllByOrganizationIdAndFormVersionIdOrderByDisplayOrderAscIdAsc(UUID organizationId, UUID versionId);
    List<FormField> findAllByOrganizationIdAndFormVersionIdIn(UUID organizationId, Collection<UUID> versionIds);

    @Modifying
    @Query("delete from FormField f where f.organizationId=:org and f.formVersionId=:versionId")
    int deleteAllByOrganizationIdAndFormVersionId(@Param("org") UUID organizationId, @Param("versionId") UUID versionId);
}
