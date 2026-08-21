package com.brainserve.onboarding.common.observability;

import org.slf4j.MDC;

public final class RequestContext {

    public static final String REQUEST_ID_KEY = "request_id";
    public static final String CORRELATION_ID_KEY = "correlation_id";

    private RequestContext() {
    }

    public static String requestId() {
        return valueOrUnknown(MDC.get(REQUEST_ID_KEY));
    }

    public static String correlationId() {
        return valueOrUnknown(MDC.get(CORRELATION_ID_KEY));
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
