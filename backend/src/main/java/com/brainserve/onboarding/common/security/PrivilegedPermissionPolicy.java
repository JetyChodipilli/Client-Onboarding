package com.brainserve.onboarding.common.security;

import java.util.Set;

/** Central definition of permissions that require an MFA-enrolled identity. */
public final class PrivilegedPermissionPolicy {
    private static final Set<String> PRIVILEGED = Set.of(
            "ORG_UPDATE",
            "USER_MANAGE",
            "ROLE_MANAGE",
            "AUDIT_READ",
            "PROJECT_ACTIVATE",
            "WORKFLOW_MANAGE",
            "FORM_MANAGE",
            "FORM_REVIEW",
            "ONBOARDING_START",
            "ONBOARDING_REVIEW",
            "ONBOARDING_APPROVE",
            "INVOICE_CREATE",
            "INVOICE_SEND",
            "PAYMENT_OVERRIDE",
            "CONTRACT_CREATE",
            "CONTRACT_SEND",
            "ASSET_REVIEW",
            "ACCESS_MANAGE",
            "ACCESS_VERIFY",
            "TASK_MANAGE",
            "NOTIFICATION_MANAGE",
            "REMINDER_MANAGE",
            "REPORT_READ");

    private PrivilegedPermissionPolicy() {
    }

    public static boolean requiresMfa(Set<String> permissions) {
        return permissions.stream().anyMatch(PRIVILEGED::contains);
    }
}
