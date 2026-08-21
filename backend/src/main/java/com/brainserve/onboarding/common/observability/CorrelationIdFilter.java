package com.brainserve.onboarding.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String REQUEST_HEADER = "X-Request-Id";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final Pattern SAFE_EXTERNAL_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestId = safeOrGenerated(request.getHeader(REQUEST_HEADER));
        String correlationId = safeOrFallback(request.getHeader(CORRELATION_HEADER), requestId);

        MDC.put(RequestContext.REQUEST_ID_KEY, requestId);
        MDC.put(RequestContext.CORRELATION_ID_KEY, correlationId);
        response.setHeader(REQUEST_HEADER, requestId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("request_completed method={} path={} status={} duration_ms={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(RequestContext.REQUEST_ID_KEY);
            MDC.remove(RequestContext.CORRELATION_ID_KEY);
        }
    }

    private static String safeOrGenerated(String candidate) {
        return isSafe(candidate) ? candidate : UUID.randomUUID().toString();
    }

    private static String safeOrFallback(String candidate, String fallback) {
        return isSafe(candidate) ? candidate : fallback;
    }

    private static boolean isSafe(String value) {
        return value != null && SAFE_EXTERNAL_ID.matcher(value).matches();
    }
}
