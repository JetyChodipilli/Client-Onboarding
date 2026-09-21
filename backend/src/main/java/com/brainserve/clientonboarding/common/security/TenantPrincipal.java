package com.brainserve.clientonboarding.common.security;

import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(
        UUID userId,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        UUID membershipId,
        String email,
        String displayName,
        String roleName,
        Set<String> permissions,
        UUID sessionId,
        boolean mfaVerified
) {
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
