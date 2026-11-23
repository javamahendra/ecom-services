package com.app.exception;

import com.app.config.RequestLoggingFilter;
import com.app.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalidRequest(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError field : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(field.getField(), field.getDefaultMessage());
        }
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Check the highlighted fields and try again.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(value ->
                errors.putIfAbsent(value.getPropertyPath().toString(), value.getMessage()));
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Check the request parameters and try again.", errors);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> invalidParameter(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER",
                "A required parameter is missing or has an invalid value.", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is missing or malformed. Check the JSON format and try again.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpServletRequest request) {
        return response(request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This HTTP method is not supported for the requested endpoint.", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> unsupportedMediaType(HttpServletRequest request) {
        return response(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "This content type is not supported. Send a supported request format.", Map.of());
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> unacceptableMediaType(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_ACCEPTABLE, "UNSUPPORTED_RESPONSE_FORMAT",
                "The requested response format is not supported.", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "The requested endpoint or resource was not found.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> conflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The update conflicts with the current data. Please try again.", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> status(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null
                ? "The request could not be completed." : exception.getReason();
        return response(request, status, HttpStatus.valueOf(status.value()).name(), message, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled cart-service API error: requestId={}, path={}",
                requestId, request.getRequestURI(), exception);
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong while processing your request. Please try again later.", Map.of());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpServletRequest request, HttpStatusCode status,
                                                       String code, String message,
                                                       Map<String, String> validationErrors) {
        int statusValue = status.value();
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), statusValue,
                HttpStatus.valueOf(statusValue).getReasonPhrase(), code, message,
                request.getRequestURI(), requestId(request), validationErrors);
        if (statusValue >= 500) {
            log.error("API error: code={}, status={}, path={}, requestId={}",
                    code, statusValue, request.getRequestURI(), body.requestId());
        } else if (statusValue >= 400) {
            log.warn("API error: code={}, status={}, path={}, requestId={}",
                    code, statusValue, request.getRequestURI(), body.requestId());
        }
        return ResponseEntity.status(status).body(body);
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE);
        return value instanceof String id ? id : UUID.randomUUID().toString();
    }
}
