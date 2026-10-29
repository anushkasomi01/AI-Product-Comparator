package io.github.anushkasomi01.laptopfinder.api;

import io.github.anushkasomi01.laptopfinder.ai.EmptyModelResponseException;
import io.github.anushkasomi01.laptopfinder.ai.ModelProviderException;
import io.github.anushkasomi01.laptopfinder.api.dto.ApiError;
import io.github.anushkasomi01.laptopfinder.observability.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .distinct()
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is invalid", violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_JSON", "A valid JSON request body is required");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMethod() {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This endpoint requires POST");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested endpoint does not exist");
    }

    @ExceptionHandler(EmptyModelResponseException.class)
    ResponseEntity<ApiError> emptyModelResponse() {
        log.warn("Model response rejected: empty answer");
        return error(HttpStatus.BAD_GATEWAY, "EMPTY_MODEL_RESPONSE",
                "The model returned no usable answer; please try again");
    }

    @ExceptionHandler(ModelProviderException.class)
    ResponseEntity<ApiError> providerFailure(ModelProviderException exception) {
        Throwable cause = exception.getCause();
        // Exception class names are useful for diagnosis; their messages may contain secrets.
        log.warn("Model request failed: causeType={}", cause == null ? "unknown" : cause.getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE",
                "The model is temporarily unavailable; please try again later");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unexpected request failure: type={}", exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return error(status, code, message, List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
            List<ApiError.FieldViolation> fieldErrors) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                MDC.get(CorrelationIdFilter.MDC_KEY), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
