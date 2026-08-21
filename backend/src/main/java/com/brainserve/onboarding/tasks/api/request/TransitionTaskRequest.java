package com.brainserve.onboarding.tasks.api.request;
import com.brainserve.onboarding.tasks.domain.model.TaskStatus;
import jakarta.validation.constraints.*;
public record TransitionTaskRequest(@NotNull TaskStatus status,@Min(0) long version){}
