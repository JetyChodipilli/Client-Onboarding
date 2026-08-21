package com.brainserve.onboarding.common.security;

import java.util.Set;
import java.util.UUID;

/** Authenticated external client identity. It is intentionally not an organization membership. */
public record ClientPrincipal(
        UUID userId,
        UUID organizationId,
        UUID sessionId,
        String email,
        String displayName,
        String organizationName,
        String organizationSlug,
        Set<String> permissions) implements AuthenticatedPrincipal {

    public ClientPrincipal {
        permissions = Set.copyOf(permissions);
    }
}
