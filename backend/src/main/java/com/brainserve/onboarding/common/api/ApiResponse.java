package com.brainserve.onboarding.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        Object meta,
        ApiError error,
        String requestId) {

    public static <T> ApiResponse<T> success(T data, Object meta, String requestId) {
        return new ApiResponse<>(true, data, meta, null, requestId);
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return success(data, Map.of(), requestId);
    }

    public static ApiResponse<Void> failure(ApiError error, String requestId) {
        return new ApiResponse<>(false, null, null, error, requestId);
    }
}
