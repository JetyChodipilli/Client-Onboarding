package com.brainserve.clientonboarding.common.observability;

import java.util.UUID;
import org.slf4j.MDC;

public final class RequestIds {

    public static final String REQUEST_ID_MDC_KEY = "request_id";
    public static final String CORRELATION_ID_MDC_KEY = "correlation_id";

    private RequestIds() {
    }

    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        return requestId == null ? UUID.randomUUID().toString() : requestId;
    }

    public static String currentCorrelationId() {
        String id = MDC.get(CORRELATION_ID_MDC_KEY);
        return id == null ? currentRequestId() : id;
    }
}
