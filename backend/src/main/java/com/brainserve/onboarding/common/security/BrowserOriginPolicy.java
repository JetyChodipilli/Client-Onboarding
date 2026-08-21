package com.brainserve.onboarding.common.security;

import com.brainserve.onboarding.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Protects refresh-cookie-only commands from same-site, cross-origin request forgery.
 * Bearer authenticated APIs do not rely on ambient browser credentials, but refresh/logout do.
 */
@Component
public class BrowserOriginPolicy {
    private final Set<String> allowedOrigins;

    public BrowserOriginPolicy(SecurityProperties properties) {
        this.allowedOrigins = new HashSet<>(properties.allowedOrigins());
    }

    public void requireTrustedOriginForCookieCommand(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            if (!allowedOrigins.contains(origin)) throw forbidden();
            return;
        }

        // Non-browser clients commonly omit Origin and Sec-Fetch-Site. Browsers making a same-site
        // or cross-site unsafe request identify that relationship; reject it if Origin is missing.
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("same-site".equalsIgnoreCase(fetchSite) || "cross-site".equalsIgnoreCase(fetchSite)) {
            throw forbidden();
        }
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "BROWSER_ORIGIN_FORBIDDEN",
                "This browser request is not allowed from the current origin.");
    }
}
