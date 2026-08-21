package com.brainserve.onboarding.contracts.domain.model;
public final class ContractStateMachine {
    private ContractStateMachine() {}
    public static boolean canTransition(ContractStatus from, ContractStatus to) {
        if (from == to) return false;
        return switch (from) {
            case DRAFT -> to == ContractStatus.GENERATED || to == ContractStatus.CANCELLED;
            case GENERATED -> to == ContractStatus.SENT || to == ContractStatus.CANCELLED || to == ContractStatus.VOID;
            case SENT -> to == ContractStatus.VIEWED || to == ContractStatus.SIGNED || to == ContractStatus.DECLINED
                    || to == ContractStatus.EXPIRED || to == ContractStatus.VOID || to == ContractStatus.CANCELLED;
            case VIEWED -> to == ContractStatus.SIGNED || to == ContractStatus.DECLINED || to == ContractStatus.EXPIRED
                    || to == ContractStatus.VOID || to == ContractStatus.CANCELLED;
            case SIGNED, DECLINED, EXPIRED, VOID, CANCELLED -> false;
        };
    }
}
