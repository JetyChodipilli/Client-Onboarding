package com.brainserve.clientonboarding.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = validUuidOrNew(request.getHeader(REQUEST_ID_HEADER));
        String correlationId = validUuidOrDefault(
                request.getHeader(CORRELATION_ID_HEADER), requestId);

        try (MDC.MDCCloseable ignoredRequest = MDC.putCloseable(
                     RequestIds.REQUEST_ID_MDC_KEY, requestId);
             MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable(
                     RequestIds.CORRELATION_ID_MDC_KEY, correlationId)) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        }
    }

    private String validUuidOrNew(String candidate) {
        return isUuid(candidate) ? candidate : UUID.randomUUID().toString();
    }

    private String validUuidOrDefault(String candidate, String fallback) {
        return isUuid(candidate) ? candidate : fallback;
    }

    private boolean isUuid(String candidate) {
        if (candidate == null || candidate.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(candidate).toString().equalsIgnoreCase(candidate);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
