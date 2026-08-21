package com.brainserve.onboarding.project.infrastructure.persistence;

import com.brainserve.onboarding.project.domain.model.ProjectMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    Optional<ProjectMember> findByOrganizationIdAndId(UUID organizationId, UUID id);
    List<ProjectMember> findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(UUID organizationId, UUID projectId);
    boolean existsByOrganizationIdAndProjectIdAndOrganizationUserId(UUID organizationId, UUID projectId, UUID organizationUserId);
}
