package com.brainserve.onboarding.client.domain.model;

/** Client lifecycle policy from the PRD. Archive remains a dedicated operation. */
public final class ClientStateMachine {
    private ClientStateMachine() {}

    public static boolean canTransition(ClientStatus current, ClientStatus target) {
        if (current == target) return current != ClientStatus.ARCHIVED;
        return (current == ClientStatus.PROSPECT && target == ClientStatus.ACTIVE)
                || (current == ClientStatus.ACTIVE && target == ClientStatus.INACTIVE);
    }
}
