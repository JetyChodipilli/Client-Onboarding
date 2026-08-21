package com.brainserve.onboarding.common.error;

import com.brainserve.onboarding.common.api.ApiError;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.api.FieldErrorDetail;
import com.brainserve.onboarding.common.observability.RequestContext;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.status())
                .body(ApiResponse.failure(ApiError.of(ex.code(), ex.getMessage()), RequestContext.requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), safeMessage(error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("VALIDATION_ERROR", "Request validation failed.", errors),
                RequestContext.requestId()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(), safeMessage(violation.getMessage())))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("VALIDATION_ERROR", "Request validation failed.", errors),
                RequestContext.requestId()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleOptimisticConflict(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                ApiError.of("VERSION_CONFLICT", "The resource changed. Refresh and try again."),
                RequestContext.requestId()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                ApiError.of("DATA_CONFLICT", "The requested change conflicts with current data."),
                RequestContext.requestId()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ApiError.of("MALFORMED_REQUEST", "The request could not be parsed."),
                RequestContext.requestId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(
                ApiError.of("INTERNAL_ERROR", "An unexpected error occurred."),
                RequestContext.requestId()));
    }

    private static String safeMessage(String value) {
        return value == null || value.isBlank() ? "Invalid value." : value;
    }
}
