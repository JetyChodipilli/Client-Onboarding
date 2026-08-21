package com.brainserve.onboarding.access;

import static org.assertj.core.api.Assertions.assertThat;
import com.brainserve.onboarding.access.domain.model.PlatformAccessRequestStatus;
import com.brainserve.onboarding.access.domain.model.PlatformAccessStateMachine;
import org.junit.jupiter.api.Test;

class PlatformAccessStateMachineTest {
    @Test void clientSubmissionNeverBecomesVerifiedDirectly(){
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.REQUESTED,PlatformAccessRequestStatus.CLIENT_SUBMITTED)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.CLIENT_SUBMITTED,PlatformAccessRequestStatus.VERIFIED)).isFalse();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.CLIENT_SUBMITTED,PlatformAccessRequestStatus.UNDER_VERIFICATION)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.UNDER_VERIFICATION,PlatformAccessRequestStatus.VERIFIED)).isTrue();
    }
    @Test void revisionRequiresResubmissionAndVerifiedIsTerminal(){
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.UNDER_VERIFICATION,PlatformAccessRequestStatus.NEEDS_REVISION)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.NEEDS_REVISION,PlatformAccessRequestStatus.CLIENT_SUBMITTED)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.VERIFIED,PlatformAccessRequestStatus.NEEDS_REVISION)).isFalse();
    }
    @Test void waiverIsLimitedToNotStartedRequestedOrRevisionStates(){
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.NOT_STARTED,PlatformAccessRequestStatus.WAIVED)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.REQUESTED,PlatformAccessRequestStatus.WAIVED)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.NEEDS_REVISION,PlatformAccessRequestStatus.WAIVED)).isTrue();
        assertThat(PlatformAccessStateMachine.canTransition(PlatformAccessRequestStatus.UNDER_VERIFICATION,PlatformAccessRequestStatus.WAIVED)).isFalse();
    }
}
