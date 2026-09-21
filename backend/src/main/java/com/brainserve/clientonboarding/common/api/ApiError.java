package com.brainserve.clientonboarding.common.api;

import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldViolation> fieldErrors
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}

