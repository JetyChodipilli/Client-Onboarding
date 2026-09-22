package com.brainserve.clientonboarding.common.error;

import com.brainserve.clientonboarding.common.api.ApiError;
import com.brainserve.clientonboarding.common.api.ApiFailure;
import com.brainserve.clientonboarding.common.api.FieldViolation;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Counter unexpectedErrors;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.unexpectedErrors = Counter.builder("application.errors.unexpected")
                .description("Unhandled API errors returned as INTERNAL_ERROR")
                .register(meterRegistry);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiFailure> handleDomainException(DomainException exception) {
        return failure(exception.status(), exception.code(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiFailure> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid.", violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiFailure> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more values are invalid.", violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiFailure> handleMethodValidation(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) return handleUnexpected(exception);
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more request parameters are invalid.", List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiFailure> handleUnreadableMessage() {
        return failure(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is missing or malformed.", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiFailure> handleAuthentication() {
        return failure(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiFailure> handleAccessDenied() {
        return failure(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "You do not have permission to perform this action.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiFailure> handleConstraintConflict() {
        return failure(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
                "The resource conflicts with an existing record.", List.of());
    }

    @ExceptionHandler(SecurityNotificationDeliveryException.class)
    ResponseEntity<ApiFailure> handleSecurityMessageFailure(SecurityNotificationDeliveryException exception) {
        log.error("Security notification delivery failed", exception);
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "SECURITY_MESSAGE_UNAVAILABLE",
                "The security message could not be delivered. Try again later.", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiFailure> handleUnexpected(Exception exception) {
        unexpectedErrors.increment();
        log.error("Unhandled request failure", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed.", List.of());
    }

    private ResponseEntity<ApiFailure> failure(
            HttpStatus status,
            String code,
            String message,
            List<FieldViolation> fieldErrors
    ) {
        ApiError error = new ApiError(code, message, fieldErrors);
        return ResponseEntity.status(status)
                .body(ApiFailure.of(error, RequestIds.currentRequestId()));
    }
}
