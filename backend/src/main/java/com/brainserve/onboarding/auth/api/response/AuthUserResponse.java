package com.brainserve.onboarding.auth.api.response;
import java.util.Set;
import java.util.UUID;
public record AuthUserResponse(UUID id, String email, String displayName, UUID organizationId,
                               String organizationName, String organizationSlug, Set<String> permissions) {
    public AuthUserResponse { permissions = Set.copyOf(permissions); }
}
