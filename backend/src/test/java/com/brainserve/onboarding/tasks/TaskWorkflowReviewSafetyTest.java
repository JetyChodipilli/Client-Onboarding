package com.brainserve.onboarding.tasks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.notifications.application.service.NotificationService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.project.application.service.ProjectWorkflowAccessService;
import com.brainserve.onboarding.tasks.api.request.TransitionTaskRequest;
import com.brainserve.onboarding.tasks.application.service.TaskAssignmentPolicy;
import com.brainserve.onboarding.tasks.application.service.TaskService;
import com.brainserve.onboarding.tasks.domain.model.TaskItem;
import com.brainserve.onboarding.tasks.domain.model.TaskPriority;
import com.brainserve.onboarding.tasks.domain.model.TaskStatus;
import com.brainserve.onboarding.tasks.domain.model.TaskType;
import com.brainserve.onboarding.tasks.infrastructure.persistence.TaskRepository;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskWorkflowReviewSafetyTest {
    @Test
    void clientCannotCompleteReviewRequiredWorkflowTaskDirectly() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID onboarding = UUID.randomUUID();
        UUID step = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-21T10:00:00Z");

        TaskItem task = new TaskItem(taskId, org, project, onboarding, step, "Upload confirmation", null,
                TaskType.WORKFLOW_GENERATED, TaskPriority.MEDIUM, user, "CLIENT", null, null,
                "workflow-step:" + step, null, "SYSTEM", now);
        TaskRepository repository = mock(TaskRepository.class);
        when(repository.findByOrganizationIdAndId(org, taskId)).thenReturn(Optional.of(task));
        when(repository.findForUpdate(org, taskId)).thenReturn(Optional.of(task));

        OnboardingStepCommandService workflow = mock(OnboardingStepCommandService.class);
        when(workflow.lockStepContext(org, onboarding, step)).thenReturn(new OnboardingStepCommandService.LockedStep(
                project, step, WorkflowStepType.MANUAL_TASK, OnboardingStepStatus.IN_PROGRESS,
                false, true, false, false, 0));

        ClientPortalAccessService clientAccess = mock(ClientPortalAccessService.class);
        TaskService service = new TaskService(repository, mock(ProjectWorkflowAccessService.class), workflow,
                mock(ActivityTimelineService.class), mock(AuditService.class), mock(OutboxService.class),
                mock(NotificationService.class), clientAccess, mock(TaskAssignmentPolicy.class),
                Clock.fixed(now, ZoneOffset.UTC));
        ClientPrincipal principal = new ClientPrincipal(user, org, UUID.randomUUID(), "client@example.com", "Client User",
                "Example Org", "example", Set.of("CLIENT_PORTAL"));

        assertThatThrownBy(() -> service.clientTransition(principal, taskId,
                new TransitionTaskRequest(TaskStatus.COMPLETED, 0), mock(HttpServletRequest.class)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("TASK_REVIEW_REQUIRED");
    }
    @Test
    void nonReviewWorkflowTaskDoesNotSubmitWorkflowWhenTaskEntersInternalReview() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID onboarding = UUID.randomUUID();
        UUID step = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-21T10:00:00Z");

        TaskItem task = new TaskItem(taskId, org, project, onboarding, step, "Internal checklist", null,
                TaskType.WORKFLOW_GENERATED, TaskPriority.MEDIUM, user, "INTERNAL", null, null,
                "workflow-step:" + step, null, "SYSTEM", now);
        task.transition(TaskStatus.IN_PROGRESS, user, "INTERNAL", now.plusSeconds(1));

        TaskRepository repository = mock(TaskRepository.class);
        when(repository.findByOrganizationIdAndId(org, taskId)).thenReturn(Optional.of(task));
        when(repository.findForUpdate(org, taskId)).thenReturn(Optional.of(task));
        OnboardingStepCommandService workflow = mock(OnboardingStepCommandService.class);
        when(workflow.lockStepContext(org, onboarding, step)).thenReturn(new OnboardingStepCommandService.LockedStep(
                project, step, WorkflowStepType.MANUAL_TASK, OnboardingStepStatus.IN_PROGRESS,
                false, false, false, false, 0));

        TaskService service = new TaskService(repository, mock(ProjectWorkflowAccessService.class), workflow,
                mock(ActivityTimelineService.class), mock(AuditService.class), mock(OutboxService.class),
                mock(NotificationService.class), mock(ClientPortalAccessService.class), mock(TaskAssignmentPolicy.class),
                Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
        TenantPrincipal principal = new TenantPrincipal(user, org, UUID.randomUUID(), UUID.randomUUID(),
                "ops@example.com", "Ops User", "Example Org", "example", Set.of("TASK_MANAGE"));

        service.transition(principal, taskId, new TransitionTaskRequest(TaskStatus.IN_REVIEW, 0), mock(HttpServletRequest.class));

        verify(workflow, never()).transition(org, onboarding, step, OnboardingStepStatus.SUBMITTED, user,
                com.brainserve.onboarding.onboarding.application.service.WorkflowActorType.INTERNAL);
    }

}
