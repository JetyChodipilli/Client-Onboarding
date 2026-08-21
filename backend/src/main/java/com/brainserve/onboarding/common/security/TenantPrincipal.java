package com.brainserve.onboarding.common.security;

import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(
        UUID userId,
        UUID organizationId,
        UUID membershipId,
        UUID sessionId,
        String email,
        String displayName,
        String organizationName,
        String organizationSlug,
        Set<String> permissions) implements AuthenticatedPrincipal {

    public TenantPrincipal {
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
