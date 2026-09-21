package com.brainserve.clientonboarding.common.security;

import com.brainserve.clientonboarding.common.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class CurrentPrincipal {
    private CurrentPrincipal() { }

    public static TenantPrincipal require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}
