package com.brainserve.clientonboarding.common.api;

import java.util.Map;

public record ApiSuccess<T>(
        boolean success,
        T data,
        Map<String, Object> meta,
        String requestId
) {
    public static <T> ApiSuccess<T> of(T data, String requestId) {
        return new ApiSuccess<>(true, data, Map.of(), requestId);
    }
}

