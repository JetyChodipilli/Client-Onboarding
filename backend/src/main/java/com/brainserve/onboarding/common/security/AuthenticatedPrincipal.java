package com.brainserve.onboarding.common.security;

import java.util.Set;
import java.util.UUID;

/** Common authenticated identity contract without collapsing internal and client authorization scopes. */
public interface AuthenticatedPrincipal {
    UUID userId();
    UUID organizationId();
    UUID sessionId();
    String email();
    String displayName();
    String organizationName();
    String organizationSlug();
    Set<String> permissions();
}
