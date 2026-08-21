package com.brainserve.onboarding.tasks.infrastructure.persistence;
import com.brainserve.onboarding.tasks.domain.model.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface TaskRepository extends JpaRepository<TaskItem,UUID>{
    Optional<TaskItem> findByOrganizationIdAndId(UUID org,UUID id);
    Optional<TaskItem> findByOrganizationIdAndSourceKey(UUID org,String sourceKey);
    Optional<TaskItem> findByOrganizationIdAndStepId(UUID org,UUID stepId);
    Page<TaskItem> findAllByOrganizationId(UUID org,Pageable pageable);
    Page<TaskItem> findAllByOrganizationIdAndAssignedUserId(UUID org,UUID userId,Pageable pageable);
    Page<TaskItem> findAllByOrganizationIdAndAssignedUserIdAndAssignedUserType(UUID org,UUID userId,String assignedUserType,Pageable pageable);
    Page<TaskItem> findAllByOrganizationIdAndProjectId(UUID org,UUID projectId,Pageable pageable);
    Page<TaskItem> findAllByOrganizationIdAndStatus(UUID org,TaskStatus status,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TaskItem t where t.organizationId=:org and t.id=:id") Optional<TaskItem> findForUpdate(@Param("org")UUID org,@Param("id")UUID id);
}
