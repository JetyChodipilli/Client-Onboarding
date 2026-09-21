package com.brainserve.clientonboarding.project.domain.repository;

import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.project.domain.model.ActivityEntry;
import com.brainserve.clientonboarding.project.domain.model.ProjectMember;
import com.brainserve.clientonboarding.project.domain.model.ProjectRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    PageSlice<ProjectRecord> findPage(UUID organizationId, String search, String status, UUID clientId,
                                      int page, int size);
    Optional<ProjectRecord> findById(UUID organizationId, UUID projectId);
    ProjectRecord insert(ProjectRecord project, UUID actorId);
    boolean update(UUID organizationId, UUID projectId, ProjectRecord replacement, long version,
                   UUID actorId, Instant now);
    boolean transition(UUID organizationId, UUID projectId, ProjectRecord.Status current,
                       ProjectRecord.Status next, ProjectRecord.Status previousStatus, long version,
                       UUID actorId, Instant now);
    List<ProjectMember> findMembers(UUID organizationId, UUID projectId);
    ProjectMember insertMember(ProjectMember member, UUID actorId);
    boolean removeMember(UUID organizationId, UUID projectId, UUID memberId);
    List<ActivityEntry> findActivity(UUID organizationId, UUID projectId, int limit);
    void appendActivity(ActivityEntry entry);
}
