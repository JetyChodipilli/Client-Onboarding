package com.brainserve.onboarding.common.config;

import com.brainserve.onboarding.common.observability.RequestContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "You are not authorized for this action.");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Request IDs are generated or accepted only after strict character validation by CorrelationIdFilter.
        // The remaining fields are application constants, keeping this filter-layer response deterministic
        // without binding the security infrastructure to a particular Jackson major version.
        String body = """
                {"success":false,"error":{"code":"%s","message":"%s","fieldErrors":[]},"requestId":"%s"}
                """.formatted(code, message, RequestContext.requestId()).strip();
        response.getWriter().write(body);
    }
}
