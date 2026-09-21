package com.brainserve.clientonboarding.auth.application;

import java.util.Set;

public final class MfaAssurance {
    private static final Set<String> PRIVILEGED_PERMISSIONS = Set.of(
            "USER_MANAGE", "ROLE_MANAGE", "AUDIT_READ", "PAYMENT_OVERRIDE", "PROJECT_ACTIVATE",
            "SERVICE_MANAGE", "WORKFLOW_MANAGE", "ONBOARDING_REVIEW", "ONBOARDING_APPROVE");

    private MfaAssurance() { }

    public static boolean requiredFor(Set<String> permissions) {
        return permissions.stream().anyMatch(PRIVILEGED_PERMISSIONS::contains);
    }
}
