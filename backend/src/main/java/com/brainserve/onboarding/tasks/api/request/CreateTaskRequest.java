package com.brainserve.onboarding.tasks.api.request;
import com.brainserve.onboarding.tasks.domain.model.TaskPriority;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record CreateTaskRequest(@NotBlank @Size(max=220) String title,@Size(max=4000) String description,UUID projectId,UUID onboardingId,UUID stepId,TaskPriority priority,UUID assignedUserId,@Pattern(regexp="INTERNAL|CLIENT") String assignedUserType,UUID assignedRoleId,Instant dueAt){}
