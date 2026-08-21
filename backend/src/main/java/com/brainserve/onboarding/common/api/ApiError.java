package com.brainserve.onboarding.common.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}
