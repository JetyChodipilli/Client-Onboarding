package com.brainserve.onboarding.tasks.application.service;

import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.onboarding.application.service.FinalReviewRevisionHandler;
import com.brainserve.onboarding.tasks.domain.model.TaskItem;
import com.brainserve.onboarding.tasks.domain.model.TaskStatus;
import com.brainserve.onboarding.tasks.domain.model.TaskType;
import com.brainserve.onboarding.tasks.infrastructure.persistence.TaskRepository;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskFinalReviewRevisionHandler implements FinalReviewRevisionHandler {
    private final TaskRepository tasks;
    private final OutboxService outbox;

    public TaskFinalReviewRevisionHandler(TaskRepository tasks, OutboxService outbox) {
        this.tasks = tasks;
        this.outbox = outbox;
    }

    @Override public boolean supports(WorkflowStepType stepType) { return stepType == WorkflowStepType.MANUAL_TASK; }

    @Override
    public void reopen(RevisionContext context) {
        TaskItem task = tasks.findByOrganizationIdAndStepId(context.organizationId(), context.stepId())
                .filter(value -> value.getTaskType() == TaskType.WORKFLOW_GENERATED && value.getStatus() == TaskStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("The workflow task is not completed and cannot be reopened."));
        task.reopenFromFinalReview(context.reviewerUserId(), context.occurredAt());
        tasks.saveAndFlush(task);
        outbox.record(context.organizationId(), "TASK_REOPENED", "TASK", task.getId(),
                Map.of("taskId", task.getId(), "stepId", context.stepId(), "projectId", context.projectId(),
                        "source", "FINAL_REVIEW"));
    }
}
