package com.brainserve.clientonboarding.common.api;

public record ApiFailure(
        boolean success,
        ApiError error,
        String requestId
) {
    public static ApiFailure of(ApiError error, String requestId) {
        return new ApiFailure(false, error, requestId);
    }
}

