package com.brainserve.onboarding.onboarding;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.application.service.SimpleWorkflowStepService;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingStepInstanceRepository;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimpleWorkflowStepSafetyTest {
    @Test
    void genericCompletionCannotBypassFeatureOwnedPaymentStep() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID onboarding = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        OnboardingStepInstance step = mock(OnboardingStepInstance.class);
        when(step.getOnboardingId()).thenReturn(onboarding);
        OnboardingStepInstanceRepository repository = mock(OnboardingStepInstanceRepository.class);
        when(repository.findByOrganizationIdAndId(org, stepId)).thenReturn(Optional.of(step));

        OnboardingStepCommandService workflow = mock(OnboardingStepCommandService.class);
        when(workflow.lockStepContext(org, onboarding, stepId)).thenReturn(new OnboardingStepCommandService.LockedStep(
                project, stepId, WorkflowStepType.PAYMENT, OnboardingStepStatus.AVAILABLE,
                true, false, false, false, 0));

        SimpleWorkflowStepService service = new SimpleWorkflowStepService(repository, workflow, mock(ClientPortalAccessService.class));
        ClientPrincipal principal = new ClientPrincipal(user, org, UUID.randomUUID(), "client@example.com", "Client User",
                "Example Org", "example", Set.of("CLIENT_PORTAL"));

        assertThatThrownBy(() -> service.completeClient(principal, project, stepId))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("STEP_ACTION_NOT_GENERIC");
    }
}
