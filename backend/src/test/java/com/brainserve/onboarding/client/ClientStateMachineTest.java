package com.brainserve.onboarding.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.client.domain.model.ClientStateMachine;
import com.brainserve.onboarding.client.domain.model.ClientStatus;
import org.junit.jupiter.api.Test;

class ClientStateMachineTest {
    @Test
    void permitsOnlyDocumentedForwardLifecycleTransitions() {
        assertThat(ClientStateMachine.canTransition(ClientStatus.PROSPECT, ClientStatus.ACTIVE)).isTrue();
        assertThat(ClientStateMachine.canTransition(ClientStatus.ACTIVE, ClientStatus.INACTIVE)).isTrue();
        assertThat(ClientStateMachine.canTransition(ClientStatus.ACTIVE, ClientStatus.PROSPECT)).isFalse();
        assertThat(ClientStateMachine.canTransition(ClientStatus.INACTIVE, ClientStatus.ACTIVE)).isFalse();
        assertThat(ClientStateMachine.canTransition(ClientStatus.ARCHIVED, ClientStatus.ACTIVE)).isFalse();
    }
}
