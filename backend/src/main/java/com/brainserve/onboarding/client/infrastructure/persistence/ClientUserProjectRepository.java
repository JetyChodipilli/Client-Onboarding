package com.brainserve.onboarding.client.infrastructure.persistence;

import com.brainserve.onboarding.client.domain.model.ClientUserProject;
import com.brainserve.onboarding.client.domain.model.ClientUserProjectStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientUserProjectRepository extends JpaRepository<ClientUserProject, UUID> {
    Optional<ClientUserProject> findByOrganizationIdAndClientUserIdAndProjectId(UUID organizationId, UUID clientUserId, UUID projectId);
    List<ClientUserProject> findAllByOrganizationIdAndClientUserIdInAndStatus(UUID organizationId, Collection<UUID> clientUserIds, ClientUserProjectStatus status);
}
