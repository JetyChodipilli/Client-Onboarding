package com.brainserve.clientonboarding.auth.application;

import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import com.brainserve.clientonboarding.auth.infrastructure.security.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieService {
    private final AuthProperties properties;

    public SessionCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public void issue(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(SessionAuthenticationFilter.SESSION_COOKIE, rawToken)
                .httpOnly(true).secure(properties.sessionCookieSecure()).sameSite("Lax").path("/")
                .maxAge(properties.sessionDuration()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SessionAuthenticationFilter.SESSION_COOKIE, "")
                .httpOnly(true).secure(properties.sessionCookieSecure()).sameSite("Lax").path("/")
                .maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
