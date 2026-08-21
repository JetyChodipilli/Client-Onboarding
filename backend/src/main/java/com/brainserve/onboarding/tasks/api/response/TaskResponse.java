package com.brainserve.onboarding.tasks.api.response;
import com.brainserve.onboarding.tasks.domain.model.*;
import java.time.Instant;
import java.util.UUID;
public record TaskResponse(UUID id,UUID projectId,UUID onboardingId,UUID stepId,String title,String description,TaskType taskType,TaskStatus status,TaskPriority priority,UUID assignedUserId,String assignedUserType,UUID assignedRoleId,Instant dueAt,Instant completedAt,Instant createdAt,Instant updatedAt,long version){}
