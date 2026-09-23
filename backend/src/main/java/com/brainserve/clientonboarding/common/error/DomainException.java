package com.brainserve.clientonboarding.common.error;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final java.util.List<com.brainserve.clientonboarding.common.api.FieldViolation> fieldErrors;

    public DomainException(String code, String message, HttpStatus status) {
        this(code, message, status, java.util.List.of());
    }

    public DomainException(String code, String message, HttpStatus status,
                           java.util.List<com.brainserve.clientonboarding.common.api.FieldViolation> fieldErrors) {
        super(message);
        this.code = code;
        this.status = status;
        this.fieldErrors = java.util.List.copyOf(fieldErrors);
    }

    public java.util.List<com.brainserve.clientonboarding.common.api.FieldViolation> fieldErrors() { return fieldErrors; }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
