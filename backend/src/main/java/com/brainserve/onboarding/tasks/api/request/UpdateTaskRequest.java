package com.brainserve.onboarding.tasks.api.request;
import com.brainserve.onboarding.tasks.domain.model.TaskPriority;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record UpdateTaskRequest(@NotBlank @Size(max=220) String title,@Size(max=4000) String description,TaskPriority priority,UUID assignedUserId,@Pattern(regexp="INTERNAL|CLIENT") String assignedUserType,UUID assignedRoleId,Instant dueAt,@Min(0) long version){}
